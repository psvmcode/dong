package com.dong.lab.order.machine;

import com.alibaba.cola.statemachine.StateMachine;
import com.alibaba.cola.statemachine.builder.StateMachineBuilder;
import com.alibaba.cola.statemachine.builder.StateMachineBuilderFactory;
import com.dong.lab.order.enums.OrderEvent;
import com.dong.lab.order.enums.OrderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
/**
 * 订单履约状态机。COLA 状态机是无状态的：它只回答「从某个状态收到某个事件该去哪」，
 * 自己不持有当前状态，所以整个实例可以被所有线程共享。
 *
 * <p>因此并发安全不归它管，得靠外层的数据库乐观锁。
 * 状态机管规则，乐观锁管并发，两件事分开做才不会互相干扰：
 * 状态机保证跳步和倒退根本走不通，乐观锁保证同时到达的两次推进只有一个生效。
 */
@Slf4j
@Component

public class OrderStateMachine {

    /**
     * 状态机标识，装配后注册进 StateMachineFactory，可按 id 取回。
     */
    public static final String MACHINE_ID = "orderFulfillment";

    /**
     * 状态机实例，装配完成后不再变化，线程安全。
     */
    private final StateMachine<OrderStatus, OrderEvent, OrderContext> stateMachine;

    /**
     * 装配全部迁移。迁移表装配完即固定，放在构造器里一次性完成，避免运行期被篡改。
     */
    public OrderStateMachine() {
        StateMachineBuilder<OrderStatus, OrderEvent, OrderContext> builder = StateMachineBuilderFactory.create();
        builder.setFailCallback((OrderStatus from, OrderEvent event, OrderContext ctx) ->
                log.info("order transition rejected orderNo={} from={} event={}", ctx.getOrderNo(), from, event));
        builder.externalTransition()
                .from(OrderStatus.WAIT_PAY)
                .to(OrderStatus.WAIT_SHIP)
                .on(OrderEvent.PAY)
                .when((OrderContext ctx) -> ctx.getPayNo() != null && !ctx.getPayNo().isBlank())
                .perform((OrderStatus from, OrderStatus to, OrderEvent event, OrderContext ctx) -> accept(ctx, to));
        builder.externalTransition()
                .from(OrderStatus.WAIT_PAY)
                .to(OrderStatus.CANCELLED)
                .on(OrderEvent.CANCEL)
                .perform((OrderStatus from, OrderStatus to, OrderEvent event, OrderContext ctx) -> accept(ctx, to));
        builder.externalTransition()
                .from(OrderStatus.WAIT_PAY)
                .to(OrderStatus.CANCELLED)
                .on(OrderEvent.TIMEOUT)
                .perform((OrderStatus from, OrderStatus to, OrderEvent event, OrderContext ctx) -> accept(ctx, to));
        // 多个来源归并到同一目标，省掉两段重复的迁移定义
        builder.externalTransitions()
                .fromAmong(OrderStatus.WAIT_SHIP, OrderStatus.WAIT_RECEIVE)
                .to(OrderStatus.REFUNDING)
                .on(OrderEvent.APPLY_REFUND)
                .when(this::refundAmountValid)
                .perform((OrderStatus from, OrderStatus to, OrderEvent event, OrderContext ctx) -> {
                    ctx.setRefundFrom(from);
                    accept(ctx, to);
                });
        builder.externalTransition()
                .from(OrderStatus.WAIT_SHIP)
                .to(OrderStatus.WAIT_RECEIVE)
                .on(OrderEvent.SHIP)
                .when((OrderContext ctx) -> ctx.getTrackingNo() != null && !ctx.getTrackingNo().isBlank())
                .perform((OrderStatus from, OrderStatus to, OrderEvent event, OrderContext ctx) -> accept(ctx, to));
        builder.externalTransition()
                .from(OrderStatus.WAIT_RECEIVE)
                .to(OrderStatus.FINISHED)
                .on(OrderEvent.RECEIVE)
                .perform((OrderStatus from, OrderStatus to, OrderEvent event, OrderContext ctx) -> accept(ctx, to));
        builder.externalTransition()
                .from(OrderStatus.REFUNDING)
                .to(OrderStatus.REFUNDED)
                .on(OrderEvent.REFUND_SUCCESS)
                .perform((OrderStatus from, OrderStatus to, OrderEvent event, OrderContext ctx) -> accept(ctx, to));
        // 同一个事件配两条迁移，靠守卫分流：退款失败要退回发起退款前的那个状态，
        // 而不是固定退回某一个。COLA 要求这种情况下每条迁移都必须带 when，否则装配期就报错
        builder.externalTransition()
                .from(OrderStatus.REFUNDING)
                .to(OrderStatus.WAIT_SHIP)
                .on(OrderEvent.REFUND_FAIL)
                .when((OrderContext ctx) -> ctx.getRefundFrom() == OrderStatus.WAIT_SHIP)
                .perform((OrderStatus from, OrderStatus to, OrderEvent event, OrderContext ctx) -> accept(ctx, to));
        builder.externalTransition()
                .from(OrderStatus.REFUNDING)
                .to(OrderStatus.WAIT_RECEIVE)
                .on(OrderEvent.REFUND_FAIL)
                .when((OrderContext ctx) -> ctx.getRefundFrom() == OrderStatus.WAIT_RECEIVE)
                .perform((OrderStatus from, OrderStatus to, OrderEvent event, OrderContext ctx) -> accept(ctx, to));
        // 内部迁移：催单不改变状态，只执行动作。within 已经把源和目标都置成同一状态，
        // 所以链路上没有 to 环节，直接接 on
        builder.internalTransition()
                .within(OrderStatus.WAIT_SHIP)
                .on(OrderEvent.URGE)
                .perform((OrderStatus from, OrderStatus to, OrderEvent event, OrderContext ctx) -> {
                    ctx.setUrge(true);
                    accept(ctx, to);
                });
        this.stateMachine = builder.build(MACHINE_ID);
    }

    /**
     * 触发一次状态迁移。结果从上下文读：accepted 为 true 才是真推进了，
     * target 是目标状态。
     *
     * @param from 当前状态
     * @param event 触发的事件
     * @param context 状态机上下文
     */
    public void fire(OrderStatus from, OrderEvent event, OrderContext context) {
        stateMachine.fireEvent(from, event, context);
    }

    /**
     * 判断当前状态是否定义了该事件的迁移。只校验有没有这条路，不校验守卫，
     * 这样可以区分「压根没这条迁移」和「有迁移但守卫没过」两种拒绝原因。
     *
     * @param from 当前状态
     * @param event 触发的事件
     * @return true 表示存在该迁移定义
     */
    public boolean supports(OrderStatus from, OrderEvent event) {
        return stateMachine.verify(from, event);
    }

    /**
     * 导出状态图，PlantUML 语法，直接贴到支持 PlantUML 的编辑器里就能看。
     *
     * @return PlantUML 文本
     */
    public String plantUml() {
        return stateMachine.generatePlantUML();
    }

    /**
     * 判断退款金额是否合法：必须大于零且不超过实付金额。
     *
     * @param context 状态机上下文
     * @return true 表示金额合法
     */
    private boolean refundAmountValid(OrderContext context) {
        BigDecimal refundAmount = context.getRefundAmount();
        BigDecimal payAmount = context.getPayAmount();
        if (refundAmount == null || payAmount == null) {
            return false;
        }
        return refundAmount.compareTo(BigDecimal.ZERO) > 0 && refundAmount.compareTo(payAmount) <= 0;
    }

    /**
     * 标记迁移被接受并记录目标状态。只有 action 真的被执行才会走到这里，
     * 这是区分「被接受」与「被拒绝」的唯一可靠依据。
     *
     * @param context 状态机上下文
     * @param target 目标状态
     */
    private void accept(OrderContext context, OrderStatus target) {
        context.setTarget(target);
        context.setAccepted(true);
    }

}
