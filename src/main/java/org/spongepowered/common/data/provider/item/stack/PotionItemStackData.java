/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.common.data.provider.item.stack;

import com.google.common.collect.ImmutableList;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionUtils;
import net.minecraft.potion.Potions;
import org.spongepowered.api.data.Keys;
import org.spongepowered.api.effect.potion.PotionEffect;
import org.spongepowered.api.item.potion.PotionType;
import org.spongepowered.api.util.Color;
import org.spongepowered.common.data.provider.DataProviderRegistrator;
import org.spongepowered.common.data.util.NbtCollectors;
import org.spongepowered.common.util.Constants;

import java.util.List;

@SuppressWarnings("unchecked")
public final class PotionItemStackData {

    private PotionItemStackData() {
    }

    // @formatter:off
    public static void register(final DataProviderRegistrator registrator) {
        registrator
                .asMutable(ItemStack.class)
                    .create(Keys.COLOR)
                        .get(h -> Color.ofRgb(PotionUtils.getColor(h)))
                        .set((h, v) -> {
                            final CompoundNBT tag = h.getOrCreateTag();
                            tag.putInt(Constants.Item.CUSTOM_POTION_COLOR, v.getRgb());
                        })
                        .delete(h -> h.removeChildTag(Constants.Item.CUSTOM_POTION_COLOR))
                        .supports(h -> h.getItem() == Items.POTION || h.getItem() == Items.SPLASH_POTION || h.getItem() == Items.LINGERING_POTION)
                    .create(Keys.POTION_EFFECTS)
                        .get(h -> {
                            final List<EffectInstance> effects = PotionUtils.getEffectsFromStack(h);
                            return effects.isEmpty() ? null : ImmutableList.copyOf((List<PotionEffect>) (Object) effects);
                        })
                        .set((h, v) -> {
                            final CompoundNBT tag = h.getOrCreateTag();
                            final ListNBT list = v.stream()
                                    .map(effect -> {
                                        final CompoundNBT potionTag = new CompoundNBT();
                                        ((EffectInstance) effect).write(potionTag);
                                        return potionTag;
                                    })
                                    .collect(NbtCollectors.toTagList());
                            tag.put(Constants.Item.CUSTOM_POTION_EFFECTS, list);
                        })
                        .delete(h -> h.removeChildTag(Constants.Item.CUSTOM_POTION_EFFECTS))
                        .supports(h -> h.getItem() == Items.POTION || h.getItem() == Items.SPLASH_POTION ||
                                h.getItem() == Items.LINGERING_POTION || h.getItem() == Items.TIPPED_ARROW)
                    .create(Keys.POTION_TYPE)
                        .get(h -> (PotionType) PotionUtils.getPotionFromItem(h))
                        .set((h, v) -> {
                            h.getOrCreateTag();
                            PotionUtils.addPotionToItemStack(h, (Potion) v);
                        })
                        .delete(h -> {
                            if (h.hasTag()) {
                                PotionUtils.addPotionToItemStack(h, Potions.EMPTY);
                            }
                        })
                        .supports(h -> h.getItem() == Items.POTION || h.getItem() == Items.SPLASH_POTION ||
                                h.getItem() == Items.LINGERING_POTION || h.getItem() == Items.TIPPED_ARROW);
    }
    // @formatter:on
}