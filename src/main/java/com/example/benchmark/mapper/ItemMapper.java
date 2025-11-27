package com.example.benchmark.mapper;

import com.example.benchmark.domain.Item;

/**
 * MyBatis Mapper interface for Item entity.
 * Provides INSERT operations for benchmark testing.
 */
public interface ItemMapper {

    /**
     * Insert a single item into the benchmark_items table.
     * Uses benchmark_items_seq.NEXTVAL for ID generation.
     *
     * @param item the item to insert
     */
    void insert(Item item);

    /**
     * Get the count of all items in the benchmark_items table.
     *
     * @return the total count of items
     */
    long count();

    /**
     * Truncate the benchmark_items table.
     * Resets the data for the next benchmark iteration.
     */
    void truncateTable();
}
