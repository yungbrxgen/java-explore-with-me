package ru.practicum.ewm.category.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.category.mapper.CategoryMapper;
import ru.practicum.ewm.category.dto.CategoryDto;
import ru.practicum.ewm.category.dto.NewCategoryDto;
import ru.practicum.ewm.category.model.Category;
import ru.practicum.ewm.category.repository.CategoryRepository;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;
    private final EventRepository eventRepository;

    @Override
    @Transactional
    public CategoryDto createCategory(NewCategoryDto newCategoryDto) {
        log.info("Создание категории: {}", newCategoryDto.getName());
        Category category = categoryMapper.toCategory(newCategoryDto);
        Category savedCategory = categoryRepository.save(category);
        return categoryMapper.toCategoryDto(savedCategory);
    }

    @Override
    @Transactional
    public void deleteCategory(Long catId) {
        log.info("Удаление категории с id={}", catId);
        Category category = categoryRepository.findById(catId)
                .orElseThrow(() -> new NotFoundException("Category with id=" + catId + " was not found"));

        if (eventRepository.existsByCategoryId(catId)) {
            throw new ConflictException("The category is not empty and cannot be deleted");
        }
        categoryRepository.delete(category);
    }

    @Override
    @Transactional
    public CategoryDto updateCategory(Long catId, CategoryDto categoryDto) {
        log.info("Обновление категории с id={}: {}", catId, categoryDto.getName());
        Category category = categoryRepository.findById(catId)
                .orElseThrow(() -> new NotFoundException("Category with id=" + catId + " was not found"));

        if (categoryRepository.existsByName(categoryDto.getName()) && !category.getName().equals(categoryDto.getName())) {
            throw new ConflictException("Category name '" + categoryDto.getName() + "' already exists");
        }

        category.setName(categoryDto.getName());
        Category updatedCategory = categoryRepository.save(category);
        return categoryMapper.toCategoryDto(updatedCategory);
    }

    @Override
    public List<CategoryDto> getCategories(int from, int size) {
        log.info("Получение списка категорий: from={}, size={}", from, size);
        Pageable pageable = PageRequest.of(from / size, size);
        return categoryRepository.findAll(pageable).getContent().stream()
                .map(categoryMapper::toCategoryDto)
                .collect(Collectors.toList());
    }

    @Override
    public CategoryDto getCategoryById(Long catId) {
        log.info("Получение категории по id={}", catId);
        Category category = categoryRepository.findById(catId)
                .orElseThrow(() -> new NotFoundException("Category with id=" + catId + " was not found"));
        return categoryMapper.toCategoryDto(category);
    }
}