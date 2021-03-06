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
package org.spongepowered.common.config.customdata;

import org.spongepowered.api.data.persistence.DataView;
import org.spongepowered.api.data.persistence.InvalidDataException;
import org.spongepowered.common.data.persistence.SerializedDataTransaction;
import org.spongepowered.common.data.persistence.datastore.SpongeCustomDataStore;
import org.spongepowered.common.util.PrettyPrinter;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

@ConfigSerializable
public final class CustomDataRegistrationCategory {

    /**
     * This isn't so much of a config option, this is more of allowing the
     * server owner to know what is registered at present time during server
     * startup. This is always auto-populated after the data manager is
     * finally baked with all data registrations.
     *
     * This should be populated after the default registrations are done.
     */
    @Setting(value = "registered-data")
    @Comment("An auto generated list, by Sponge, to provide a list of \n"
                 + "registered custom data manipulators by plugins. Since \n"
                 + "the list is generated AFTER the game starts, modifying \n"
                 + "this list will not affect Sponge's system in any way. \n"
                 + "However, it is advisable to view what registered datas \n"
                 + "exist on a server instance, such that when Sponge completes \n"
                 + "startup, it will be verified that all existing registrations \n"
                 + "are accounted for. A warning will be emitted for any existing \n"
                 + "registrations that were not registered, and moved to the \n"
                 + "'failed-data-list'.")
    public final Set<String> registeredData = new ConcurrentSkipListSet<>();

    /**
     * Again, another auto-populated list of FAILED id's. This is never
     * read from file, except after startup, to specify which id's can
     * be "saved".
     */
    @Setting(value = "failed-data")
    @Comment("An auto generated list, by Sponge, to discover and list \n"
             + "all failed custom data deserializations at runtime due \n"
             + "to a lack of the registrations being made by a plugin. \n"
             + "Not to be confused by failed deserialization due to bad data. \n"
             + "Modifying the list will result in no effect as Sponge auto \n"
             + "generates this list. This is merely for user configuration.")
    public final Set<String> failedData = new ConcurrentSkipListSet<>();

    /**
     * This is a configurable list of id's that are to be "purged" on
     * discovery.
     */
    @Setting(value = "data-to-purge")
    @Comment("A configurable list of registration ids that are to be removed \n"
                 + "when discovered for deserialization. This can be controlled by \n"
                 + "commands in sponge. It is adviseable to refer to the lists made \n"
                 + "available through 'failed-data-list', as using any id's from \n"
                 + "'registered-data' will result in custom data being deleted at \n"
                 + "every load.")
    public final Set<String> dataToPurge = new ConcurrentSkipListSet<>();

    @Setting(value = "print-failed-data")
    @Comment("In the cases where there is already previously discovered data \n"
             + "we don't want to spam the log on each discovery in certain \n"
             + "contexts. If it is required, we still can emit the log warning \n"
             + "when necessary.")
    public boolean printFailedData = false;

    public void populateRegistrations(final Collection<SpongeCustomDataStore> stores) {
        this.registeredData.clear();
        for (final SpongeCustomDataStore store : stores) {
            this.registeredData.add(store.getCustomDataKey().getFormatted());
        }
    }

    public void addFailedData(final String dataId, final Throwable cause) {
        if (this.failedData.add(dataId) && this.printFailedData) {
            new PrettyPrinter(60).add("Failed Data Discovery").centre().hr()
                .addWrapped("Sponge found an unregistered DataRegistration id. Don't worry though!"
                            + "Sponge will attempt to persist the failed data for future attempts, unless"
                            + "the id is added in 'data-to-purge' in the sponge/custom-data.cnf")
                .add()
                .add("%s : %s", "Unregistered Id", dataId)
                .add(new InvalidDataException("Could not deserialize " + dataId + "!", cause))
                .trace();
        }
    }

    public void purgeOrAllow(final SerializedDataTransaction.Builder builder, final String data, final DataView view) {
        if (!this.dataToPurge.contains(data)) {
            builder.failedData(view);
        }
    }
}
