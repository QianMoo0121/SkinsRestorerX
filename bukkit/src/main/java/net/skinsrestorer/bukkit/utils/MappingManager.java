/*
 * SkinsRestorer
 *
 * Copyright (C) 2022 SkinsRestorer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 */
package net.skinsrestorer.bukkit.utils;

import com.google.common.collect.ImmutableList;
import net.skinsrestorer.mappings.mapping1_18.Mapping1_18;
import net.skinsrestorer.mappings.mapping1_18_2.Mapping1_18_2;
import net.skinsrestorer.mappings.mapping1_19.Mapping1_19;
import net.skinsrestorer.mappings.mapping1_19_3.Mapping1_19_3;
import net.skinsrestorer.mappings.mapping1_19_4.Mapping1_19_4;
import net.skinsrestorer.mappings.shared.IMapping;
import org.bukkit.Bukkit;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

public class MappingManager {
    private static final List<IMapping> mappings = ImmutableList.<IMapping>builder()
            .add(new Mapping1_18())
            .add(new Mapping1_18_2())
            .add(new Mapping1_19())
            .add(new Mapping1_19_3())
            .add(new Mapping1_19_4())
            .build();

    public static Optional<IMapping> getMapping() {
        String mappingVersion = getMappingsVersion();

        for (IMapping mapping : mappings) {
            if (mapping.getSupportedVersions().contains(mappingVersion)) {
                return Optional.of(mapping);
            }
        }

        return Optional.empty();
    }

    @SuppressWarnings({"deprecation"})
    public static String getMappingsVersion() {
        org.bukkit.UnsafeValues craftMagicNumbers = Bukkit.getServer().getUnsafe();
        try {
            Method method = craftMagicNumbers.getClass().getMethod("getMappingsVersion");
            return (String) method.invoke(craftMagicNumbers, new Object[0]);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException | ClassCastException e) {
            return null;
        }
    }
}
