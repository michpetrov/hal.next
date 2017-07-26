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
package org.jboss.hal.client.configuration.subsystem.datasource.wizard;

import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import elemental2.dom.HTMLElement;
import org.jboss.hal.ballroom.form.Form;
import org.jboss.hal.ballroom.form.PropertiesItem;
import org.jboss.hal.ballroom.wizard.WizardStep;
import org.jboss.hal.core.mbui.form.ModelNodeForm;
import org.jboss.hal.dmr.ModelNode;
import org.jboss.hal.meta.Metadata;
import org.jboss.hal.resources.Ids;
import org.jboss.hal.resources.Resources;

import static org.jboss.hal.dmr.ModelDescriptionConstants.VALUE;
import static org.jboss.hal.dmr.ModelNodeHelper.failSafeGet;

class PropertiesStep extends WizardStep<Context, State> {

    private final ModelNode dummy;
    private final Form<ModelNode> form;
    private final PropertiesItem propertiesItem;

    PropertiesStep(Metadata metadata, Resources resources) {
        super(resources.constants().xaProperties());

        dummy = new ModelNode().setEmptyObject();
        propertiesItem = new PropertiesItem(VALUE);
        propertiesItem.setRequired(true);
        ModelNode propertiesDescription = failSafeGet(metadata.getDescription(),
                "attributes/value/description"); //NON-NLS
        form = new ModelNodeForm.Builder<>(Ids.DATA_SOURCE_PROPERTIES_FORM, Metadata.empty())
                .unboundFormItem(propertiesItem, 0, SafeHtmlUtils.fromString(propertiesDescription.asString()))
                .build();
        registerAttachable(form);
    }

    @Override
    public HTMLElement asElement() {
        return form.asElement();
    }

    @Override
    protected void onShow(Context context) {
        propertiesItem.setValue(context.xaProperties);
        propertiesItem.setUndefined(false);
        propertiesItem.setEnabled(!context.isCreated()); // can only be changed if DS was not already created
        form.edit(dummy);
    }

    @Override
    protected boolean onNext(Context context) {
        boolean valid = form.save();
        if (valid) {
            context.xaProperties.clear();
            context.xaProperties.putAll(propertiesItem.getValue());
        }
        return valid;
    }

    @Override
    protected boolean onBack(Context context) {
        form.cancel();
        return true;
    }

    @Override
    protected boolean onCancel(Context context) {
        form.cancel();
        return true;
    }
}
