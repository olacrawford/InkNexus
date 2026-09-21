package com.inknexus.common.result;

import lombok.Data;

import java.util.List;

/**
 * 分页结果包装类。
 *
 * <p>相比直接返回 MyBatis-Plus 的 IPage，只暴露前端真正需要的字段，
 * 避免序列化出 optimizeCountSql、searchCount 等内部字段。</p>
 */
@Data
public class PageResult<T> {

    /** 当前页数据 */
    private List<T> records;
    private long total;
    private long pages;
    private long current;
    private long size;

    public PageResult() {
    }

    /** 全参构造：字段顺序对应 MyBatis-Plus IPage，service 层用它把查询结果转成本结构。 */
    public PageResult(List<T> records, long total, long pages, long current, long size) {
        this.records = records;
        this.total = total;
        this.pages = pages;
        this.current = current;
        this.size = size;
    }
}
