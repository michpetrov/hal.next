/*
 * Copyright 2015-2016 Red Hat, Inc, and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.hal.core.mbui.form;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.hal.ballroom.form.CompositeFormItem;
import org.jboss.hal.ballroom.form.CreationContext;
import org.jboss.hal.ballroom.form.FormItem;
import org.jboss.hal.ballroom.form.FormItemProvider;
import org.jboss.hal.dmr.ModelNode;
import org.jboss.hal.dmr.ModelType;
import org.jboss.hal.dmr.Property;

import static org.jboss.hal.dmr.ModelDescriptionConstants.*;

/**
 *
 * @author Michal Petrov
 *
 */

public class ObjectFormItem extends CompositeFormItem {

    private Map<String, FormItem> items;
    private Data data;

    protected ObjectFormItem(Property objectDescription, FormItemProvider formItemProvider) {
        super(objectDescription.getName(), new CreationContext<>(new Data(objectDescription, formItemProvider)));
    }

    @Override
    protected List<FormItem> createFormItems(CreationContext context) {
        this.data = (Data) context.data();
        items = new HashMap<>();
        for (Property prop : data.getAttrDescriptions()) {
            Property clone = prop.clone();
            boolean isRequired = !clone.getValue().get(NILLABLE).asBoolean();
            // item must not be required if its parent isn't
            clone.getValue().get(NILLABLE).set(!(isRequired && data.isParentRequired()));

            items.put(prop.getName(), data.getFormItemProvider().createFrom(clone));
        }

        return new ArrayList<>(items.values());
    }

    @Override
    protected void populateFormItems(ModelNode modelNode) {
        List<Property> properties = modelNode.asPropertyList();
        for (Property prop : properties) {
            String compositeName = data.getParentName() + "." + prop.getName();
            FormItem item = items.get(compositeName);
            data.getMapping().populateItemByType(modelNode.get(prop.getName()), item);
        }
    }

    @Override
    protected void persistModel(ModelNode modelNode) {
        boolean isUndefined = true;
        ModelNode tempNode = new ModelNode().setEmptyObject();
        for (FormItem item : items.values()) {
            if (!item.isUndefined()) {
                isUndefined = false;
            }
            String itemName = item.getName().substring(data.getParentName().length() + 1);
            data.getMapping().persistValueByType(tempNode, item);
            modelNode.get(itemName).set(tempNode.get(item.getName()));
        }

        if (isUndefined) {
            // all attributes are undefined -> make the parent attribute undefined
            modelNode.clear();
        }
    }

    /**
     * overriden to skip adding valueChangeHandler
     */
    @Override
    public void attach() {
        for (FormItem item : items.values()) {
            item.attach();
            item.addValueChangeHandler(event -> setModified(true));
        }
    }

    @Override
    public boolean validate() {
        boolean valid = super.validate();
        if (data.isParentRequired()) {
            return valid;
        }

        /*
         * the parent is not required but if it contains any required attributes
         * they must either be all be undefined or all must be set
         */
        List<FormItem> undefinedItems = new ArrayList<>();
        String[] names = new String[]{};
        int totalItems = 0;
        for (Property prop : data.getAttrDescriptions()) {
            if (prop.getValue().get(NILLABLE).asBoolean()) {
                continue;
            }

            FormItem item = items.get(prop.getName());
            if (item.isUndefined()) {
                undefinedItems.add(item);
            }
            names[totalItems] = item.getLabel();
            totalItems++;
        }
        if (!undefinedItems.isEmpty() && undefinedItems.size() != totalItems) {
            for (FormItem fi : undefinedItems) {
                fi.showError("Value cannot be empty. Each or none of [" + names + "] has to be set");
            }
            return false;
        }

        return valid;
    }

    static class Data {
        private FormItemProvider formItemProvider;
        private List<Property> attrDescriptions;
        private boolean isParentRequired;
        private String parentName;
        private ModelNodeMapping<ModelNode> mapping;

        Data(Property objectDescription, FormItemProvider formItemProvider) {
            this.formItemProvider = formItemProvider;
            Property clone = objectDescription.clone();
            this.parentName = objectDescription.getName();
            this.isParentRequired = !clone.getValue().get(NILLABLE).asBoolean();
            this.attrDescriptions = new ArrayList<>();
            for (Property prop : clone.getValue().get(VALUE_TYPE).asPropertyList()) {
                this.attrDescriptions.add(new Property(parentName + "." + prop.getName(), prop.getValue()));
            }
            this.mapping = new ModelNodeMapping<>(this.attrDescriptions);
        }

        public FormItemProvider getFormItemProvider() {
            return formItemProvider;
        }

        public void setFormItemProvider(FormItemProvider formItemProvider) {
            this.formItemProvider = formItemProvider;
        }

        public List<Property> getAttrDescriptions() {
            return attrDescriptions;
        }

        public void setObjectDescription(List<Property> attrDescriptions) {
            this.attrDescriptions = attrDescriptions;
        }

        public boolean isParentRequired() {
            return isParentRequired;
        }

        public void setParentRequired(boolean isParentRequired) {
            this.isParentRequired = isParentRequired;
        }

        public ModelNodeMapping<ModelNode> getMapping() {
            return mapping;
        }

        public void setMapping(ModelNodeMapping<ModelNode> mapping) {
            this.mapping = mapping;
        }

        public String getParentName() {
            return parentName;
        }
    }
}
