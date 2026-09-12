package com.inknexus.book.service.impl;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.inknexus.book.entity.Category;
import com.inknexus.book.mapper.CategoryMapper;
import com.inknexus.book.service.CategoryService;
import com.inknexus.book.vo.CategoryVO;
import com.inknexus.common.exception.BusinessException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;

    public CategoryServiceImpl(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

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

    // 限流触发时返回友好提示
    public List<CategoryVO> listCategoriesBlocked(BlockException e) {
        throw new BusinessException(429, "分类列表请求过于频繁，请稍后再试");
    }
}
