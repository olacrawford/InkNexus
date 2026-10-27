package com.inknexus.book.service.impl;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.inknexus.book.entity.Category;
import com.inknexus.book.mapper.CategoryMapper;
import com.inknexus.book.service.CategoryService;
import com.inknexus.book.vo.CategoryVO;
import com.inknexus.common.constant.ErrorCode;
import com.inknexus.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 分类业务实现：只读接口，结果整体缓存进 SpringCache 的 category 缓存
 * （基准 TTL 60 分钟，比 books 更长——分类变化频率低，见 {@code RedisConfig}）。
 */
@Service
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    private CategoryMapper categoryMapper;

    /** 查询启用分类，按 sort 升序，结果整体缓存进 category 缓存 */
    @Override
    @Cacheable(cacheNames = "category")
    @SentinelResource(value = "listCategories", blockHandler = "listCategoriesBlocked")
    public List<CategoryVO> listCategories() {
        return categoryMapper.selectList(
                new LambdaQueryWrapper<Category>()
                        .eq(Category::getStatus, 1)
                        .orderByAsc(Category::getSort)
        ).stream()
                .map(category -> new CategoryVO(
                        category.getId(),
                        category.getName(),
                        category.getSort()
                ))
                .collect(Collectors.toList());
    }

    public List<CategoryVO> listCategoriesBlocked(BlockException e) {
        throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "分类列表请求过于频繁，请稍后再试");
    }
}
