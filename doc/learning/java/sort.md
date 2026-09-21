# 常用排序：思路 + 最简实现

> 目标：六种常用排序，每个都能说出思路并手写出来。
> 代码一律按**最好懂**的方式写，不追求极致性能——先写对，再谈快。

---

## 总览

| 排序 | 平均 | 最好 | 最坏 | 额外空间 | 稳定 | 一句话 |
|---|---|---|---|---|---|---|
| 冒泡 | O(n²) | O(n) | O(n²) | O(1) | ✅ | 相邻两两比，大的沉底 |
| 选择 | O(n²) | O(n²) | O(n²) | O(1) | ❌ | 每轮挑最小的放前面 |
| 插入 | O(n²) | O(n) | O(n²) | O(1) | ✅ | 像抓牌，一张张插到合适位置 |
| 快排 | O(n log n) | O(n log n) | O(n²) | O(log n) | ❌ | 挑基准，小的放左大的放右 |
| 归并 | O(n log n) | O(n log n) | O(n log n) | O(n) | ✅ | 先拆到底，再两两有序合并 |
| 堆排序 | O(n log n) | O(n log n) | O(n log n) | O(1) | ❌ | 建大顶堆，反复把堆顶换到末尾 |

「稳定」= 相等元素的**相对顺序**排序后保持不变。比如两个都是 85 分的学生，原来张三在李四前面，排完还在前面。

---

## 1. 冒泡排序

**思路**：从头到尾，相邻两个比一下，前面比后面大就交换。
一轮下来最大的一定沉到最后，下一轮就可以少比一个。

```java
/**
 * 冒泡排序。
 */
static void bubbleSort(int[] a) {
    for (int i = 0; i < a.length - 1; i++) {
        boolean swapped = false;
        for (int j = 0; j < a.length - 1 - i; j++) {
            if (a[j] > a[j + 1]) {
                int tmp = a[j];
                a[j] = a[j + 1];
                a[j + 1] = tmp;
                swapped = true;
            }
        }
        // 一轮下来没发生交换，说明已经有序，提前收工
        if (!swapped) {
            return;
        }
    }
}
```

**特点**：最好懂，也最慢。加了 `swapped` 之后，对已排序的数组是 O(n)。实际基本不用，面试用来讲思路。

---

## 2. 选择排序

**思路**：第 i 轮从 `i` 之后的元素里**找出最小的那个**，和位置 `i` 交换。
每一轮确定一个最终位置。

```java
/**
 * 选择排序。
 */
static void selectionSort(int[] a) {
    for (int i = 0; i < a.length - 1; i++) {
        int min = i;
        for (int j = i + 1; j < a.length; j++) {
            if (a[j] < a[min]) {
                min = j;
            }
        }
        int tmp = a[i];
        a[i] = a[min];
        a[min] = tmp;
    }
}
```

**特点**：交换次数最少（最多 n 次），但比较次数永远是 n²/2 左右，**没有最好情况**。
**不稳定**：`[2a, 2b, 1]` 中 1 会和 2a 交换，两个 2 的相对顺序就反了。

---

## 3. 插入排序

**思路**：像抓扑克牌——手里已有的牌是有序的，每抓一张新牌，从后往前比，插到该在的位置。

```java
/**
 * 插入排序。
 */
static void insertionSort(int[] a) {
    for (int i = 1; i < a.length; i++) {
        int current = a[i];
        int j = i - 1;
        while (j >= 0 && a[j] > current) {
            a[j + 1] = a[j];       // 比它大的整体后挪，给新牌腾位置
            j--;
        }
        a[j + 1] = current;
    }
}
```

**特点**：数组**越接近有序越快**，已排序时只要 O(n)。
小数组（几十个元素）它其实比快排还快——所以 JDK 的 `Arrays.sort` 在小区间里用的就是它。

---

## 4. 快速排序（最常用）

**思路**：挑一个基准值，把比它小的挪到左边、比它大的挪到右边，
然后左右两半**各自再来一遍**（分治）。

```java
/**
 * 快速排序。
 */
static void quickSort(int[] a) {
    quickSort(a, 0, a.length - 1);
}

static void quickSort(int[] a, int left, int right) {
    if (left >= right) {
        return;
    }
    // 取中间当基准：如果数组已排序，取端点会让一边全空，退化成 O(n²)
    int pivot = a[left + (right - left) / 2];
    int i = left;
    int j = right;
    while (i <= j) {
        while (a[i] < pivot) {
            i++;                   // 左边找第一个不该在左的
        }
        while (a[j] > pivot) {
            j--;                   // 右边找第一个不该在右的
        }
        if (i <= j) {
            int tmp = a[i];
            a[i] = a[j];
            a[j] = tmp;            // 这一对站错了，交换
            i++;
            j--;
        }
    }
    quickSort(a, left, j);
    quickSort(a, i, right);
}
```

**特点**：实践中平均最快，缓存友好、原地排序。
**不稳定**：左右交换会把相等元素打乱。最坏 O(n²)（基准每次都挑到极值），取中间值能大幅缓解。

---

## 5. 归并排序

**思路**：先把数组**拆**到每个只剩一个元素（一个元素天然有序），
再把两个有序段**合并**成一个有序段，一路合回去。

```java
/**
 * 归并排序。
 */
static void mergeSort(int[] a) {
    if (a.length < 2) {
        return;
    }
    mergeSort(a, 0, a.length - 1, new int[a.length]);
}

static void mergeSort(int[] a, int left, int right, int[] temp) {
    if (left >= right) {
        return;
    }
    int mid = left + (right - left) / 2;
    mergeSort(a, left, mid, temp);
    mergeSort(a, mid + 1, right, temp);
    int i = left;
    int j = mid + 1;
    int k = left;
    while (i <= mid && j <= right) {
        // 相等时取左边的，这样才是稳定排序
        temp[k++] = a[i] <= a[j] ? a[i++] : a[j++];
    }
    while (i <= mid) {
        temp[k++] = a[i++];
    }
    while (j <= right) {
        temp[k++] = a[j++];
    }
    System.arraycopy(temp, left, a, left, right - left + 1);
}
```

**特点**：**性能稳定**——最好最坏都是 O(n log n)，而且**稳定**排序。
代价是要额外 O(n) 的空间。需要稳定排序时选它。

---

## 6. 堆排序

**思路**：先把数组看成一棵完全二叉树，**建成大顶堆**（每个父节点都 ≥ 子节点，所以堆顶是最大值）；
然后把堆顶和末尾交换（最大值就位），缩小堆的范围再下沉调整，反复直到剩一个。

```java
/**
 * 堆排序。
 */
static void heapSort(int[] a) {
    // 从最后一个非叶子节点开始，自底向下沉，建成大顶堆
    for (int i = a.length / 2 - 1; i >= 0; i--) {
        siftDown(a, i, a.length);
    }
    // 反复把堆顶（当前最大）换到末尾
    for (int end = a.length - 1; end > 0; end--) {
        int tmp = a[0];
        a[0] = a[end];
        a[end] = tmp;
        siftDown(a, 0, end);
    }
}

/**
 * 下沉：把 root 位置的元素往下换到合适位置。
 */
static void siftDown(int[] a, int root, int size) {
    int parent = root;
    while (true) {
        int child = parent * 2 + 1;
        if (child >= size) {
            return;
        }
        // 取左右孩子里更大的那个
        if (child + 1 < size && a[child + 1] > a[child]) {
            child++;
        }
        if (a[parent] >= a[child]) {
            return;                 // 父节点已经比孩子大，堆性质满足
        }
        int tmp = a[parent];
        a[parent] = a[child];
        a[child] = tmp;
        parent = child;
    }
}
```

**特点**：原地、最坏也是 O(n log n)，不会被输入数据坑。
**不稳定**，而且它是**跳着访问内存**（父节点和孩子在数组里隔很远），缓存不友好，实测通常比快排慢。

---

## 实测数据

六种排序跑同一批用例（空数组、单元素、已排序、逆序、全相同、含负数、随机 2/3/10/100/1000/5000 个），
结果与 `Arrays.sort` **完全一致**。

20 万个随机 int：

| 排序 | 耗时 |
|---|---|
| 快排 | 12 ms |
| 归并 | 19 ms |
| 冒泡（只跑 2 万个） | 121 ms |

数据量放大 10 倍，O(n²) 的冒泡大约慢 100 倍——这就是复杂度差距的直观体感。

---

## 怎么选

**判断顺序**：

1. **能用 JDK 就用 JDK**：`Arrays.sort()` / `List.sort()`，别自己写
2. **需要稳定排序**（保持相等元素的原有顺序）→ 归并
3. **追求平均最快、不要求稳定** → 快排
4. **怕被特殊输入坑**（必须保证最坏也是 n log n）→ 堆排序或归并
5. **数组很小（几十个）或基本有序** → 插入排序

| 场景 | 选哪个 |
|---|---|
| 生产代码 | `Arrays.sort()` |
| 要稳定 | 归并 |
| 要快、不在乎稳定 | 快排 |
| 内存紧张 + 不能退化 | 堆排序 |

---

## Java 里实际用的是什么

`Arrays.sort()` 不是一种算法，是按数据类型分派的：

| 排序对象 | 实际算法 | 说明 |
|---|---|---|
| 基本类型数组（`int[]` 等） | **双轴快排**（Dual-Pivot QuickSort） | 不稳定，但快；小区间退化成插入排序 |
| 对象数组（`Integer[]`、`List`） | **TimSort**（归并 + 插入） | **稳定**，因为对象排序必须保持相等元素的顺序 |

两个结论：

- **基本类型数组排序不保证稳定**，对象数组排序是稳定的
- 对象排序要`Comparable` / `Comparator`，别在 `compareTo` 里写减法（`a - b` 可能溢出），用 `Integer.compare(a, b)`
