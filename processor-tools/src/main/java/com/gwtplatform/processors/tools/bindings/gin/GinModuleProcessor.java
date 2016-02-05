/*
 * Copyright 2015 ArcBees Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.gwtplatform.processors.tools.bindings.gin;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.tools.FileObject;

import com.google.auto.service.AutoService;
import com.google.common.base.Optional;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.gwtplatform.processors.tools.AbstractContextProcessor;
import com.gwtplatform.processors.tools.bindings.BindingContext;
import com.gwtplatform.processors.tools.bindings.BindingsProcessor;
import com.gwtplatform.processors.tools.domain.Type;
import com.gwtplatform.processors.tools.outputter.OutputType;

@AutoService(BindingsProcessor.class)
public class GinModuleProcessor extends AbstractContextProcessor<BindingContext, Void> implements BindingsProcessor {
    public static final Type META_INF_TYPE = new Type("", "gwtp/ginModules");

    private static final String TEMPLATE = "com/gwtplatform/processors/tools/bindings/gin/GinModule.vm";

    private final Multimap<Type, GinBinding> bindings;
    private final Multimap<Type, Type> subModules;
    private final Map<Type, FileObject> sourceFiles;

    private BufferedWriter metaInfWriter;

    public GinModuleProcessor() {
        bindings = HashMultimap.create();
        subModules = HashMultimap.create();
        sourceFiles = new HashMap<>();
    }

    @Override
    public Void process(BindingContext context) {
        Type moduleType = findOrCreateSourceFile(context);

        if (context.isSubModule()) {
            createSubModule(context, moduleType);
        } else if (context.getImplementer() != null) {
            createBinding(context, moduleType);
        }

        return null;
    }

    private Type findOrCreateSourceFile(BindingContext context) {
        Type moduleType = context.getModuleType();
        if (!sourceFiles.containsKey(moduleType)) {
            createSourceFile(moduleType);
        }

        return moduleType;
    }

    private void createSourceFile(Type moduleType) {
        FileObject file = outputter.prepareSourceFile(moduleType);
        sourceFiles.put(moduleType, file);

        appendToMetaInf(moduleType);
    }

    private void appendToMetaInf(Type moduleType) {
        if (metaInfWriter == null) {
            createMetaInfFile();
        }

        try {
            metaInfWriter.append(moduleType.getQualifiedName());
            metaInfWriter.newLine();
        } catch (IOException e) {
            logger.mandatoryWarning()
                    .throwable(e)
                    .log("Unable to append '%s' to the GIN modules metadata file", moduleType);
        }
    }

    /**
     * TODO: If this file already exists in the current resources (not JARs!), append content This is not critical as it
     * should not be manually created. In the case someone wants to register modules, we can add a @GwtpModule
     * annotation
     */
    private void createMetaInfFile() {
        try {
            FileObject fileObject = outputter.prepareSourceFile(META_INF_TYPE, OutputType.META_INF);
            metaInfWriter = new BufferedWriter(fileObject.openWriter());
        } catch (IOException e) {
            logger.error().throwable(e).log("Could not to create GIN modules metadata file.");
        }
    }

    private void createSubModule(BindingContext context, Type moduleType) {
        Type implementer = context.getImplementer();

        subModules.put(moduleType, implementer);
    }

    private void createBinding(BindingContext context, Type moduleType) {
        Type implementer = context.getImplementer();

        Optional<Type> implemented = context.getImplemented();
        Optional<Type> scope = context.getScope();
        GinBinding binding =
                new GinBinding(implementer, implemented.orNull(), scope.orNull(), context.isEagerSingleton());

        bindings.put(moduleType, binding);
    }

    @Override
    public void processLast() {
        for (Map.Entry<Type, FileObject> entry : sourceFiles.entrySet()) {
            Type moduleType = entry.getKey();
            logger.debug("Generating GIN module `%s`.", moduleType.getQualifiedName());

            outputter
                    .configure(TEMPLATE)
                    .withParam("bindings", bindings.get(moduleType))
                    .withParam("subModules", subModules.get(moduleType))
                    .writeTo(moduleType, entry.getValue());
        }

        closeMetadataFile();
    }

    private void closeMetadataFile() {
        if (metaInfWriter != null) {
            try {
                metaInfWriter.close();
            } catch (IOException e) {
                logger.error().throwable(e).log("Could not write GIN modules metadata file.");
            }
        }
    }
}
