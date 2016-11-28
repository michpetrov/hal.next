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

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.base.Strings;
import org.jboss.hal.ballroom.form.DefaultMapping;
import org.jboss.hal.ballroom.form.Form;
import org.jboss.hal.ballroom.form.FormItem;
import org.jboss.hal.ballroom.form.ModelNodeItem;
import org.jboss.hal.dmr.ModelNode;
import org.jboss.hal.dmr.ModelType;
import org.jboss.hal.dmr.Property;
import org.jetbrains.annotations.NonNls;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.stream.Collectors.toList;
import static org.jboss.hal.dmr.ModelDescriptionConstants.TYPE;
import static org.jboss.hal.dmr.ModelType.BIG_INTEGER;
import static org.jboss.hal.dmr.ModelType.EXPRESSION;
import static org.jboss.hal.dmr.ModelType.INT;

/**
 * @author Harald Pehl
 */
class ModelNodeMapping<T extends ModelNode> extends DefaultMapping<T> {

    @NonNls private static final Logger logger = LoggerFactory.getLogger(ModelNodeMapping.class);

    private final List<Property> attributeDescriptions;
    private Form<T> currentForm;

    ModelNodeMapping(final List<Property> attributeDescriptions) {
        this.attributeDescriptions = attributeDescriptions;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void populateFormItems(final T model, final Form<T> form) {
        currentForm = form;
        for (FormItem formItem : form.getBoundFormItems()) {
            formItem.clearError();

            String name = formItem.getName();
            if (model.hasDefined(name)) {
                ModelNode attributeDescription = findAttribute(name);
                if (attributeDescription == null) {
                    logger.error("{}: Unable to populate form item '{}': No attribute description found in\n{}",
                            id(currentForm), name, attributeDescriptions);
                    continue;
                }

                ModelNode value = model.get(name);
                ModelType valueType = value.getType();
                if (valueType == EXPRESSION) {
                    if (formItem.supportsExpressions()) {
                        formItem.setExpressionValue(value.asString());
                    } else {
                        logger.error(
                                "{}: Unable to populate form item '{}': Value is an expression, but form item does not support expressions",
                                id(currentForm), name);
                        continue;
                    }

                } else if (formItem instanceof ModelNodeItem) {
                    formItem.setValue(value);

                } else {
                    populateItemByType(model.get(name), formItem);
                }
                formItem.setUndefined(false);

            } else {
                formItem.clearValue();
                formItem.setUndefined(true);
            }
        }
    }

    public void populateItemByType(ModelNode value, FormItem formItem) {
        String name = formItem.getName();
        ModelNode attributeDescription = findAttribute(name);
        ModelType descriptionType = attributeDescription.get(TYPE).asType();
        switch (descriptionType) {
            case BOOLEAN:
                formItem.setValue(value.asBoolean());
                break;

            case INT:
                // NumberFormItem uses *always* long
                try {
                    formItem.setValue((long) value.asInt());
                } catch (IllegalArgumentException e) {
                    logger.error(
                            "{}: Unable to populate form item '{}': Metadata says it's an INT, but value is not '{}'",
                            id(currentForm), formItem.getName(), value.asString());

                }
                break;
            case BIG_INTEGER:
            case LONG:
                try {
                    formItem.setValue(value.asLong());
                } catch (IllegalArgumentException e) {
                    logger.error(
                            "{}: Unable to populate form item '{}': Metadata says it's a {}, but value is not '{}'",
                            id(currentForm), formItem.getName(), descriptionType.name(), value.asString());
                }
                break;

            case LIST:
                List<String> list = value.asList().stream().map(ModelNode::asString).collect(toList());
                formItem.setValue(list);
                break;

            case OBJECT:
                List<Property> properties = value.asPropertyList();
                Map<String, String> map = new HashMap<>();
                for (Property property : properties) {
                    map.put(property.getName(), property.getValue().asString());
                }
                formItem.setValue(map);
                break;

            case STRING:
                formItem.setValue(value.asString());
                break;

            // unsupported types
            case BIG_DECIMAL:
            case DOUBLE:
            case BYTES:
            case EXPRESSION:
            case PROPERTY:
            case TYPE:
            case UNDEFINED:
                logger.warn("{}: populating form field '{}' of type '{}' not implemented", id(currentForm), formItem.getName(),
                        descriptionType);
                break;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void persistModel(final T model, final Form<T> form) {
        for (FormItem formItem : form.getBoundFormItems()) {
            String name = formItem.getName();

            if (formItem.isModified()) {
                ModelNode attributeDescription = findAttribute(name);
                if (attributeDescription == null) {
                    logger.error("{}: Unable to persist attribute '{}': No attribute description found in\n{}",
                            id(form), name, attributeDescriptions);
                    continue;
                }
                ModelType type = attributeDescription.get(TYPE).asType();
                Object value = formItem.getValue();

                if (formItem instanceof ModelNodeItem) {
                    model.get(name).set(((ModelNodeItem) formItem).getValue());

                } else {

                }
            }
        }
    }

    public void persistValueByType(final T model, FormItem formItem) {
        String name = formItem.getName();
        ModelNode attributeDescription = findAttribute(name);
        ModelType type = attributeDescription.get(TYPE).asType();
        Object value = formItem.getValue();
        switch (type) {
            case BOOLEAN:
                model.get(name).set((Boolean) value);
                break;

            case BIG_INTEGER:
            case INT:
            case LONG:
                Long longValue = (Long) value;
                if (longValue == null) {
                    failSafeRemove(model, name);
                } else {
                    if (type == BIG_INTEGER) {
                        model.get(name).set(BigInteger.valueOf(longValue));
                    } else if (type == INT) {
                        model.get(name).set(longValue.intValue());
                    } else {
                        model.get(name).set(longValue);
                    }
                }
                break;

            case LIST:
                List<String> list = (List<String>) value;
                if (list.isEmpty()) {
                    failSafeRemove(model, name);
                } else {
                    ModelNode listNode = new ModelNode();
                    for (String s : list) {
                        listNode.add(s);
                    }
                    model.get(name).set(listNode);
                }
                break;

            case OBJECT:
                Map<String, String> map = (Map<String, String>) value;
                if (map.isEmpty()) {
                    failSafeRemove(model, name);
                } else {
                    ModelNode mapNode = new ModelNode();
                    for (Map.Entry<String, String> entry : map.entrySet()) {
                        mapNode.get(entry.getKey()).set(entry.getValue());
                    }
                    model.get(name).set(mapNode);
                }
                break;

            case STRING:
                String stringValue = String.valueOf(value);
                if (Strings.isNullOrEmpty(stringValue)) {
                    failSafeRemove(model, name);
                } else {
                    model.get(name).set(stringValue);
                }
                break;

            // unsupported types
            case BIG_DECIMAL:
            case BYTES:
            case DOUBLE:
            case EXPRESSION:
            case PROPERTY:
            case TYPE:
            case UNDEFINED:
                logger.warn("{}: persisting form field '{}' to type '{}' not implemented", id(currentForm), name,
                        type);
                break;
        }
    }

    private void failSafeRemove(T model, String attribute) {
        if (model.isDefined()) {
            model.remove(attribute);
        }
    }

    private ModelNode findAttribute(final String name) {
        for (Property property : attributeDescriptions) {
            if (name.equals(property.getName())) {
                return property.getValue();
            }
        }
        return null;
    }

    private String id(Form<T> form) {
        return "form(" + form.getId() + ")"; //NON-NLS
    }
}
