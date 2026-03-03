package ru.practicum.ewm.category.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.category.dto.CategoryDto;
import ru.practicum.ewm.category.dto.NewCategoryDto;
import ru.practicum.ewm.category.mapper.CategoryMapper;
import ru.practicum.ewm.category.model.Category;
import ru.practicum.ewm.category.repository.CategoryRepository;
import ru.practicum.ewm.exception.NotFoundException;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional
    public CategoryDto createCategory(NewCategoryDto dto) {
        log.info("Создание категории: {}", dto.getName());
        Category category = CategoryMapper.toCategory(dto);
        Category saved = categoryRepository.save(category);
        log.info("Категория создана с id={}", saved.getId());
        return CategoryMapper.toCategoryDto(saved);
    }

    @Transactional
    public CategoryDto updateCategory(Long catId, CategoryDto dto) {
        log.info("Обновление категории id={}: {}", catId, dto.getName());
        Category category = getCategoryById(catId);
        category.setName(dto.getName());
        Category updated = categoryRepository.save(category);
        log.info("Категория id={} обновлена", catId);
        return CategoryMapper.toCategoryDto(updated);
    }

    @Transactional
    public void deleteCategory(Long catId) {
        log.info("Удаление категории id={}", catId);
        if (!categoryRepository.existsById(catId)) {
            throw new NotFoundException("Категория с id=" + catId + " не найдена");
        }
        categoryRepository.deleteById(catId);
        log.info("Категория id={} удалена", catId);
    }

    public List<CategoryDto> getCategories(int from, int size) {
        log.info("Получение категорий: from={}, size={}", from, size);
        Pageable pageable = PageRequest.of(from / size, size);
        List<Category> categories = categoryRepository.findAll(pageable).getContent();
        log.info("Найдено {} категорий", categories.size());
        return categories.stream()
                .map(CategoryMapper::toCategoryDto)
                .collect(Collectors.toList());
    }

    public CategoryDto getCategoryDtoById(Long catId) {
        log.info("Получение категории id={}", catId);
        Category category = getCategoryById(catId);
        return CategoryMapper.toCategoryDto(category);
    }

    public Category getCategoryById(Long catId) {
        return categoryRepository.findById(catId)
                .orElseThrow(() -> new NotFoundException("Категория с id=" + catId + " не найдена"));
    }
}
