package com.wildmare.wmorder.config;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;

import java.util.*;

public final class CategoryRegistry {
    public record Category(String id, String rawName, Component name, Material icon, Set<Material> materials) {
        public Category { materials = Set.copyOf(materials); }
    }

    private final Map<String, Category> categories;
    private final Map<Material, String> materialToCategory;

    public CategoryRegistry(Collection<Category> values) {
        Map<String, Category> byId = new LinkedHashMap<>();
        Map<Material, String> reverse = new EnumMap<>(Material.class);
        for (Category category : values) {
            byId.put(category.id().toLowerCase(Locale.ROOT), category);
            category.materials().forEach(material -> reverse.putIfAbsent(material, category.id()));
        }
        this.categories = Collections.unmodifiableMap(byId);
        this.materialToCategory = Collections.unmodifiableMap(reverse);
    }

    public Optional<Category> find(String id) {
        return Optional.ofNullable(id == null ? null : categories.get(id.toLowerCase(Locale.ROOT)));
    }
    public Collection<Category> all() { return categories.values(); }
    public String categoryFor(Material material) { return materialToCategory.get(material); }
}
