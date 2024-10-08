/*
 *  Copyright 2022 Red Hat
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.jboss.hal.client.installer;

import java.util.List;

import org.jboss.elemento.ElementsBag;
import org.jboss.elemento.HtmlContentBuilder;
import org.jboss.elemento.IsElement;
import org.jboss.hal.ballroom.Format;
import org.jboss.hal.ballroom.LabelBuilder;
import org.jboss.hal.ballroom.Tabs;
import org.jboss.hal.ballroom.table.Table;
import org.jboss.hal.core.finder.PreviewAttributes;
import org.jboss.hal.core.finder.PreviewAttributes.PreviewAttribute;
import org.jboss.hal.core.finder.PreviewContent;
import org.jboss.hal.core.mbui.table.ModelNodeTable;
import org.jboss.hal.dmr.ModelNode;
import org.jboss.hal.dmr.Operation;
import org.jboss.hal.dmr.dispatch.Dispatcher;
import org.jboss.hal.meta.Metadata;
import org.jboss.hal.meta.StatementContext;
import org.jboss.hal.resources.Ids;
import org.jboss.hal.resources.Resources;

import elemental2.dom.HTMLDivElement;
import elemental2.dom.HTMLElement;
import elemental2.dom.HTMLUListElement;

import static org.jboss.elemento.Elements.*;
import static org.jboss.hal.dmr.ModelDescriptionConstants.*;
import static org.jboss.hal.resources.CSS.*;

class UpdatePreview extends PreviewContent<UpdateItem> {

    private final Dispatcher dispatcher;
    private final StatementContext statementContext;

    private final Tabs tabs;
    private final Table<ModelNode> artifactChangesInTabs;
    private final Table<ModelNode> artifactChangesStandalone;
    private final HTMLElement artifactChangesStandaloneElement;
    private final ChannelChangesElement channelChangesInTabs;
    private final ChannelChangesElement channelChangesStandalone;
    private final HTMLElement channelChangesStandaloneElement;

    UpdatePreview(final UpdateItem updateItem,
            final Dispatcher dispatcher,
            final StatementContext statementContext,
            final Resources resources) {
        super(updateItem.getName());
        this.dispatcher = dispatcher;
        this.statementContext = statementContext;

        artifactChangesInTabs = new ModelNodeTable.Builder<ModelNode>(
                Ids.build(Ids.UPDATE_MANAGER_ARTIFACT_CHANGES, Ids.TABLE, "1"),
                Metadata.staticDescription(UpdateManagerResources.INSTANCE.artifactChange()))
                .columns(NAME, STATUS, OLD_VERSION, NEW_VERSION)
                .paging(false)
                .build();
        registerAttachable(artifactChangesInTabs);
        artifactChangesStandalone = new ModelNodeTable.Builder<ModelNode>(
                Ids.build(Ids.UPDATE_MANAGER_ARTIFACT_CHANGES, Ids.TABLE, "2"),
                Metadata.staticDescription(UpdateManagerResources.INSTANCE.artifactChange()))
                .columns(NAME, STATUS, OLD_VERSION, NEW_VERSION)
                .paging(false)
                .build();
        registerAttachable(artifactChangesStandalone);

        channelChangesInTabs = new ChannelChangesElement();
        channelChangesStandalone = new ChannelChangesElement();

        tabs = new Tabs(Ids.build(HISTORY, Ids.build(Ids.UPDATE_MANAGER_UPDATE, Ids.TAB_CONTAINER)));
        tabs.add(Ids.build(Ids.build(Ids.UPDATE_MANAGER_ARTIFACT_CHANGES, Ids.TAB)), resources.constants().artifactChanges(),
                div().css(marginTopLarge, marginBottomLarge)
                        .add(artifactChangesInTabs)
                        .element());
        tabs.add(Ids.build(Ids.build(Ids.UPDATE_MANAGER_CHANNEL_CHANGES, Ids.TAB)), resources.constants().channelChanges(),
                div().css(marginTopLarge, marginBottomLarge)
                        .add(channelChangesInTabs)
                        .element());

        PreviewAttributes<UpdateItem> attributes = new PreviewAttributes<>(updateItem);
        attributes.append(model -> new PreviewAttribute(new LabelBuilder().label(TIMESTAMP),
                Format.mediumDateTime(updateItem.getTimestamp())));
        attributes.append(TYPE);
        attributes.append(model -> {
            final HTMLUListElement list = ul().element();
            for (String version : model.getVersions()) {
                list.appendChild(li().textContent(version).element());
            }
            return new PreviewAttribute(new LabelBuilder().label(CHANNEL_VERSIONS), list);
        });
        previewBuilder()
                .addAll(attributes)
                .add(tabs)
                .add(artifactChangesStandaloneElement = div()
                        .add(h(2, resources.constants().artifactChanges()).css(marginBottomLarge))
                        .add(artifactChangesStandalone)
                        .element())
                .add(channelChangesStandaloneElement = div()
                        .add(h(2, resources.constants().channelChanges()).css(marginBottomLarge))
                        .add(channelChangesStandalone)
                        .element());

        setVisible(tabs, false);
        setVisible(artifactChangesStandaloneElement, false);
        setVisible(channelChangesStandaloneElement, false);
    }

    @Override
    public void update(final UpdateItem item) {
        Operation operation = new Operation.Builder(AddressTemplates.INSTALLER_TEMPLATE.resolve(statementContext),
                HISTORY_FROM_REVISION)
                .param(REVISION, item.getName())
                .build();
        dispatcher.execute(operation, result -> {
            boolean hasChannelChanges = result.get(CHANNEL_CHANGES).isDefined();
            boolean hasArtifactChanges = result.get(ARTIFACT_CHANGES).isDefined();

            if (hasArtifactChanges && hasChannelChanges) {
                setVisible(tabs, true);
            } else {
                setVisible(artifactChangesStandaloneElement, hasArtifactChanges);
                setVisible(channelChangesStandaloneElement, hasChannelChanges);
            }

            if (hasArtifactChanges) {
                artifactChangesInTabs.update(result.get(ARTIFACT_CHANGES).asList());
                artifactChangesStandalone.update(result.get(ARTIFACT_CHANGES).asList());
            }
            if (hasChannelChanges) {
                channelChangesInTabs.update(result.get(CHANNEL_CHANGES).asList());
                channelChangesStandalone.update(result.get(CHANNEL_CHANGES).asList());
            }
        });
    }

    private static class ChannelChangesElement implements IsElement<HTMLElement> {

        private final HtmlContentBuilder<HTMLDivElement> root;
        private final LabelBuilder labelBuilder;

        ChannelChangesElement() {
            root = div();
            labelBuilder = new LabelBuilder();
        }

        @Override
        public HTMLElement element() {
            return root.element();
        }

        void update(List<ModelNode> nodes) {
            removeChildrenFrom(root);
            for (ModelNode node : nodes) {
                ElementsBag bag = new ElementsBag();

                ModelNode manifestNode = node.get(MANIFEST);
                StringBuilder manifestBuilder = new StringBuilder();
                if (manifestNode.get(OLD_MANIFEST).isDefined()) {
                    manifestBuilder.append(manifestNode.get(OLD_MANIFEST).asString())
                            .append(" → ");
                }
                manifestBuilder.append(manifestNode.get(NEW_MANIFEST).asString());

                HtmlContentBuilder<HTMLUListElement> ul;
                bag.add(h(4).textContent(node.get(CHANNEL_NAME).asString()))
                        .add(ul().css(listGroup)
                                .add(li().css(listGroupItem)
                                        .add(span().css(key).textContent(labelBuilder.label(STATUS)))
                                        .add(span().css(value).textContent(node.get(STATUS).asString())))
                                .add(li().css(listGroupItem)
                                        .add(span().css(key).textContent(labelBuilder.label(MANIFEST)))
                                        .add(span().css(value).textContent(manifestBuilder.toString())))
                                .add(li().css(listGroupItem)
                                        .add(span().css(key).textContent(labelBuilder.label(REPOSITORIES)))
                                        .add(span().css(value).add(ul = ul()))));
                List<ModelNode> repositories = node.get(REPOSITORIES).asList();
                for (ModelNode repository : repositories) {
                    StringBuilder repositoryBuilder = new StringBuilder();
                    if (repository.get(OLD_REPOSITORY).isDefined()) {
                        repositoryBuilder.append(repository.get(OLD_REPOSITORY).asString()).append(" → ");
                    }
                    repositoryBuilder.append(repository.get(NEW_REPOSITORY).asString());
                    ul.add(li().textContent(repositoryBuilder.toString()));
                }
                root.addAll(bag.elements());
            }
        }
    }
}
