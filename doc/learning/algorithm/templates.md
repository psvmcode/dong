# 算法模板：高频题型的固定写法

> 目标：给出可直接套用的代码模板。**背模板不是为了抄，是为了把注意力留给题目本身的变形。**

---

## 总原则

**先识别题型，再套模板，最后处理边界。**

看到题目的判断顺序：

```
1. 数组/字符串 + 有序？        → 二分
2. 求「最长/最短/所有满足的子串/子数组」？ → 滑动窗口
3. 链表？                      → 快慢指针 / 虚拟头节点
4. 树？                        → 递归遍历 / 层序
5. 求「所有方案/组合/排列」？    → 回溯
6. 求「最优解/最多/最少」+ 可分解？ → 动态规划
7. 求「前 K 大/第 K 大」？      → 堆
```

---

## 一、二分查找

### 最容易错的地方

**边界条件**：`while (left <= right)` 还是 `<`？
`right = mid - 1` 还是 `mid`？`mid` 会不会溢出？

### 统一模板（闭区间写法）

```java
public int binarySearch(int[] nums, int target) {
    int left = 0;
    int right = nums.length - 1;          // 闭区间 [left, right]

    while (left <= right) {               // 注意是 <=
        int mid = left + (right - left) / 2;   // 防溢出，别写 (left+right)/2

        if (nums[mid] == target) {
            return mid;
        } else if (nums[mid] < target) {
            left = mid + 1;               // 缩小到 [mid+1, right]
        } else {
            right = mid - 1;              // 缩小到 [left, mid-1]
        }
    }
    return -1;
}
```

**记忆点**：
- 搜索区间是**闭区间** `[left, right]` → 循环条件是 `<=`
- 排除 mid 后，新区间是 `[mid+1, right]` 或 `[left, mid-1]`
- `mid` 用 `left + (right - left) / 2` 防止整数溢出

### 查找左边界（第一个等于 target 的位置）

**区别**：找到 target 时**不立即返回**，而是继续向左收缩。

```java
public int leftBound(int[] nums, int target) {
    int left = 0, right = nums.length - 1;
    while (left <= right) {
        int mid = left + (right - left) / 2;
        if (nums[mid] == target) {
            right = mid - 1;              // ← 不返回，继续往左找
        } else if (nums[mid] < target) {
            left = mid + 1;
        } else {
            right = mid - 1;
        }
    }
    // 检查是否越界、是否真的找到了
    if (left >= nums.length || nums[left] != target) return -1;
    return left;
}
```

**右边界**：对称地，相等时 `left = mid + 1`。

### 适用

不只是「查找某个值」，凡是**能判断「某个条件下是否可行」且答案单调**的都能用二分：
「求最小的满足条件的 x」这类问题——**二分答案**。

---

## 二、双指针

### 1. 对撞指针（有序数组的两数之和）

```java
public int[] twoSum(int[] nums, int target) {
    int left = 0, right = nums.length - 1;
    while (left < right) {
        int sum = nums[left] + nums[right];
        if (sum == target) return new int[]{left, right};
        else if (sum < target) left++;     // 和太小，左指针右移
        else right--;                      // 和太大，右指针左移
    }
    return new int[]{-1, -1};
}
```

**核心**：利用有序性，一次移动就能排除一批可能。

### 2. 快慢指针（链表）

**判断链表有环**：

```java
public boolean hasCycle(ListNode head) {
    ListNode slow = head, fast = head;
    while (fast != null && fast.next != null) {
        slow = slow.next;          // 慢：一步
        fast = fast.next.next;     // 快：两步
        if (slow == fast) return true;    // 相遇 → 有环
    }
    return false;
}
```

**找链表中点**、**找倒数第 k 个**、**判断回文**都是这个套路。

---

## 三、滑动窗口

### 适用

**求「最长/最短的满足某条件的连续子串/子数组」**。

### 模板

```java
public int slidingWindow(String s) {
    Map<Character, Integer> window = new HashMap<>();
    int left = 0, right = 0;
    int result = 0;

    while (right < s.length()) {
        char c = s.charAt(right);
        right++;                              // ① 扩大窗口
        window.put(c, window.getOrDefault(c, 0) + 1);

        // ② 判断左侧是否要收缩（有些题是 while，有些是 if）
        while (需要收缩) {
            char d = s.charAt(left);
            left++;                           // 缩小窗口
            window.put(d, window.get(d) - 1);
        }

        // ③ 更新答案（位置取决于求最长还是最短）
        result = Math.max(result, right - left);
    }
    return result;
}
```

### 三步记忆

```
① 增大窗口（right++，更新统计）
② 收缩窗口（left++，直到不再满足条件）   ← while 而不是 if
③ 更新答案
```

### 一个关键点

**「最长」和「最短」的答案更新位置不同**：

- 求**最长**：窗口合法时更新（通常在收缩之后）
- 求**最短**：收缩过程中更新（每次收缩都试一次）

搞混就会差一。

---

## 四、二叉树遍历

### 递归模板（最简单，优先写这个）

```java
// 前序：根 左右
void preorder(TreeNode root) {
    if (root == null) return;
    visit(root);              // ← 这行的位置决定了是前/中/后序
    preorder(root.left);
    preorder(root.right);
}
```

| 遍历 | visit 的位置 |
|---|---|
| 前序（根左右） | **最前面** |
| 中序（左根右） | 中间（左递归之后） |
| 后序（左右根） | **最后面** |

**二叉搜索树（BST）中序遍历 = 升序序列**，这个性质经常用。

### 层序遍历（BFS）

```java
public List<List<Integer>> levelOrder(TreeNode root) {
    List<List<Integer>> result = new ArrayList<>();
    if (root == null) return result;

    Queue<TreeNode> queue = new LinkedList<>();
    queue.offer(root);

    while (!queue.isEmpty()) {
        int size = queue.size();              // ← 关键：先固定本层节点数
        List<Integer> level = new ArrayList<>();

        for (int i = 0; i < size; i++) {       // 只处理本层
            TreeNode node = queue.poll();
            level.add(node.val);
            if (node.left != null) queue.offer(node.left);
            if (node.right != null) queue.offer(node.right);
        }
        result.add(level);
    }
    return result;
}
```

**关键**：内层循环用**固定的 size**，否则会把下一层的节点也算进本层。

---

## 五、回溯（求所有方案）

### 适用

**组合、排列、子集、切割、棋盘**这类「求所有可能」的问题。

### 模板

```java
List<List<Integer>> result = new ArrayList<>();
LinkedList<Integer> path = new LinkedList<>();

void backtrack(int[] nums, int start) {
    if (满足结束条件) {
        result.add(new ArrayList<>(path));    // ← 必须 new，否则引用被改
        return;
    }

    for (int i = start; i < nums.length; i++) {
        // 剪枝：不合条件的跳过
        if (需要剪枝) continue;

        path.add(nums[i]);          // ① 做选择
        backtrack(nums, i + 1);     // ② 递归
        path.removeLast();          // ③ 撤销选择  ← 最容易忘
    }
}
```

### 三个关键点

| 要点 | 说明 |
|---|---|
| **必须撤销选择** | `path.removeLast()`，否则前面的选择会污染后面的分支 |
| **结果要 new 一份** | `new ArrayList<>(path)`，直接加 path 的话最后全是空的 |
| **start 参数控制去重** | 组合问题传 `i+1`（不重复选），排列问题传 `0` + used 数组 |

**剪枝是回溯的灵魂**——不剪枝的回溯必然超时。

---

## 六、动态规划

### 思考步骤（按这个顺序想）

```
1. 定义 dp 数组的含义     ← 最重要，定义错了全错
2. 找出递推关系（状态转移方程）
3. 确定初始值（base case）
4. 确定遍历顺序
```

**第 1 步最关键**：`dp[i]` 到底表示什么，必须一句话说清楚。
比如「`dp[i]` = 以第 i 个元素结尾的最长递增子序列长度」。

### 两个经典模型

**模型一：0-1 背包**（每件物品选或不选）

```java
// dp[i][w] = 前 i 个物品，容量 w 时的最大价值
for (int i = 1; i <= n; i++) {
    for (int w = W; w >= weight[i]; w--) {      // ← 倒序遍历！
        dp[w] = Math.max(dp[w], dp[w - weight[i]] + value[i]);
    }
}
```

**0-1 背包内层必须倒序遍历**（保证每个物品只用一次）。
完全背包（物品可重复选）才是正序。

**模型二：最长递增子序列**

```java
// dp[i] = 以 nums[i] 结尾的最长递增子序列长度
int[] dp = new int[n];
Arrays.fill(dp, 1);                    // 每个元素至少长度为 1
for (int i = 1; i < n; i++) {
    for (int j = 0; j < i; j++) {
        if (nums[i] > nums[j]) {
            dp[i] = Math.max(dp[i], dp[j] + 1);
        }
    }
}
```

---

## 七、TopK 问题（堆）

### 求「前 K 大」

```java
public int[] topK(int[] nums, int k) {
    PriorityQueue<Integer> heap = new PriorityQueue<>();   // 默认小顶堆

    for (int num : nums) {
        heap.offer(num);
        if (heap.size() > k) {
            heap.poll();          // ← 超过 k 个就把最小的踢掉
        }
    }
    // 堆里剩下的就是最大的 k 个
    return heap.stream().mapToInt(Integer::intValue).toArray();
}
```

### 关键理解

| 问题 | 用什么堆 | 为什么 |
|---|---|---|
| 前 K **大** | **小顶堆** | 堆顶是候选里最小的，新元素比它大就替换 |
| 前 K **小** | **大顶堆** | 对称 |

**记忆**：**求大用小堆，求小用大堆**（反着的）。

### 复杂度优势

```
排序：O(n log n)，需要 O(n) 空间
堆：  O(n log K)，只需 O(K) 空间     ← n 很大、K 很小时优势巨大
```

**「10 亿个数里找最大的 100 个」** —— 必须用堆，排序内存都放不下。

---

## 八、链表题的通用技巧

### 虚拟头节点（dummy node）

**处理「头节点可能被删除」的情况**，避免写一堆 if：

```java
ListNode dummy = new ListNode(0);
dummy.next = head;
ListNode cur = dummy;
// ... 操作
return dummy.next;        // ← 返回真正的头
```

**只要涉及删除且可能删头节点，就用 dummy。**

### 反转链表（迭代）

```java
public ListNode reverse(ListNode head) {
    ListNode prev = null;
    ListNode cur = head;
    while (cur != null) {
        ListNode next = cur.next;    // 先存下一个
        cur.next = prev;             // 反转指针
        prev = cur;                  // prev 前进
        cur = next;                  // cur 前进
    }
    return prev;                     // prev 是新的头
}
```

四行循环，**顺序不能乱**：先存 next，再改指针，再移动。

---

## 记忆口诀

| 题型 | 识别特征 | 模板要点 |
|---|---|---|
| **二分** | 有序 / 答案单调 | 闭区间 `left <= right`，`mid` 防溢出 |
| **对撞指针** | 有序数组两数 | 和大右移、和小左移 |
| **快慢指针** | 链表环/中点 | 一步 vs 两步 |
| **滑动窗口** | 最长/最短连续子串 | 扩 → 收缩(while) → 更新 |
| **二叉树** | 树 | 递归最简；层序要先固定 size |
| **回溯** | 所有方案 | 做选择 → 递归 → **撤销** |
| **DP** | 最优解、可分解 | 先定义 dp 含义；0-1 背包**倒序** |
| **TopK** | 前 K 大/小 | **求大用小堆**，O(n log K) |
| **链表** | 可能删头 | **dummy 节点** |
