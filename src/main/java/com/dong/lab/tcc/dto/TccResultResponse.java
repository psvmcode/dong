package com.dong.lab.tcc.dto;

/**
 * TCC 事务结果响应。
 */
public class TccResultResponse {

    /**
     * 是否已提交。
     */
    private boolean committed;

    /**
     * 全局事务 id。
     */
    private String xid;

    /**
     * 结果提示信息。
     */
    private String message;

    /**
     * 构造事务提交成功的响应。
     */
    public static TccResultResponse committed(String xid) {
        TccResultResponse response = new TccResultResponse();
        response.setCommitted(true);
        response.setXid(xid);
        response.setMessage("confirmed");
        return response;
    }

    /**
     * 构造事务回滚的响应。
     */
    public static TccResultResponse rolledBack(String xid, String message) {
        TccResultResponse response = new TccResultResponse();
        response.setCommitted(false);
        response.setXid(xid);
        response.setMessage(message);
        return response;
    }

    /**
     * 获取是否已提交。
     */
    public boolean isCommitted() {
        return committed;
    }

    /**
     * 设置是否已提交。
     */
    public void setCommitted(boolean committed) {
        this.committed = committed;
    }

    /**
     * 获取全局事务 id。
     */
    public String getXid() {
        return xid;
    }

    /**
     * 设置全局事务 id。
     */
    public void setXid(String xid) {
        this.xid = xid;
    }

    /**
     * 获取结果提示信息。
     */
    public String getMessage() {
        return message;
    }

    /**
     * 设置结果提示信息。
     */
    public void setMessage(String message) {
        this.message = message;
    }

}
