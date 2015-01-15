package com.magnoliales.handlebars.utils;

import com.magnoliales.handlebars.annotations.Page;
import com.magnoliales.handlebars.templates.HandlebarsTemplateDefinition;
import com.magnoliales.handlebars.templates.HandlebarsTemplateDefinitionProvider;
import info.magnolia.context.MgnlContext;
import info.magnolia.i18nsystem.SimpleTranslator;
import info.magnolia.rendering.template.TemplateAvailability;
import info.magnolia.rendering.template.configured.ConfiguredTemplateAvailability;
import info.magnolia.rendering.template.registry.TemplateDefinitionRegistry;
import info.magnolia.repository.RepositoryConstants;
import info.magnolia.ui.dialog.registry.DialogDefinitionRegistry;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.ocm.mapper.Mapper;
import org.apache.jackrabbit.ocm.mapper.impl.annotation.AnnotationMapperImpl;
import org.apache.jackrabbit.ocm.mapper.impl.annotation.Node;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import javax.jcr.query.Row;
import java.util.*;

// @todo add checks for initializations in every method
public class HandlebarsRegistryImpl implements HandlebarsRegistry {

    private static Logger logger = LoggerFactory.getLogger(HandlebarsRegistryImpl.class);

    private boolean initialized;
    // make the key to be string and not class, easier that way
    private Map<Class<?>, HandlebarsTemplateDefinition> templateDefinitions;
    private TemplateDefinitionRegistry templateDefinitionRegistry;
    private DialogDefinitionRegistry dialogDefinitionRegistry;
    private SimpleTranslator translator;

    @Inject
    public HandlebarsRegistryImpl(TemplateDefinitionRegistry templateDefinitionRegistry,
                                  DialogDefinitionRegistry dialogDefinitionRegistry,
                                  SimpleTranslator translator) {

        this.templateDefinitionRegistry = templateDefinitionRegistry;
        this.dialogDefinitionRegistry = dialogDefinitionRegistry;
        this.translator = translator;
    }

    @Override
    public void init(String... namespaces) {

        if (initialized) {
            throw new IllegalStateException("Handlebars registry is already initialized");
        }

        Set<Class> nodeClasses = new HashSet<>();
        Set<Class<?>> pageClasses = new HashSet<>();
        for (String namespace : namespaces) {

            logger.info("Processing the namespace '{}'", namespace);
            Reflections reflections = new Reflections(namespace);

            pageClasses.addAll(reflections.getTypesAnnotatedWith(Page.class));
        }

        logger.info("Registering the node types '{}'", nodeClasses);
        processDefinitions(pageClasses, new HashMap<Class<?>, HandlebarsTemplateDefinition>());
    }

    @Override
    public List<HandlebarsTemplateDefinition> getTemplateDefinitions() {
        List<HandlebarsTemplateDefinition> definitionsList = new ArrayList<>(templateDefinitions.values());
        Collections.sort(definitionsList);
        return definitionsList;
    }

    private void processDefinitions(Set<Class<?>> unprocessedClasses,
                                    Map<Class<?>, HandlebarsTemplateDefinition> processedDefinitions) {

        if (unprocessedClasses.isEmpty()) {
            this.templateDefinitions = processedDefinitions;
            return;
        }
        for (Class<?> pageClass : unprocessedClasses) {
            Class<?> pageSuperclass = getPageSuperclass(pageClass);
            if (pageSuperclass == null || processedDefinitions.containsKey(pageSuperclass)) {
                TemplateAvailability templateAvailability = new ConfiguredTemplateAvailability();
                HandlebarsTemplateDefinition parent = null;
                if (pageSuperclass != null) {
                    parent = processedDefinitions.get(pageSuperclass);
                }
                HandlebarsTemplateDefinition handlebarsTemplateDefinition =
                        new HandlebarsTemplateDefinition(pageClass, templateAvailability, translator, parent);
                HandlebarsTemplateDefinitionProvider handlebarsTemplateDefinitionProvider =
                        new HandlebarsTemplateDefinitionProvider(handlebarsTemplateDefinition);
                templateDefinitionRegistry.register(handlebarsTemplateDefinitionProvider);
                processedDefinitions.put(pageClass, handlebarsTemplateDefinition);
            }
        }
        unprocessedClasses.removeAll(processedDefinitions.keySet());
        processDefinitions(unprocessedClasses, processedDefinitions);
    }

    private Class<?> getPageSuperclass(Class<?> pageClass) {
        if (pageClass == Object.class) {
            return null;
        }
        Class<?> superclass = pageClass.getSuperclass();
        if (superclass.isAnnotationPresent(Page.class)) {
            return superclass;
        } else {
            return getPageSuperclass(superclass);
        }
    }

    @Override
    public boolean pageWithTemplateExists(String template) {
        return getPagesByTemplate(template).size() > 0;
    }

    @Override
    public Map<String, String> getPagesByTemplate(String template) {
        Map<String, String> pages = new TreeMap<>();
        String expression = "SELECT * FROM [mgnl:page] WHERE [mgnl:template] = '" + template + "'";
        try {
            Session session = MgnlContext.getJCRSession(RepositoryConstants.WEBSITE);
            QueryManager queryManager = session.getWorkspace().getQueryManager();
            Query query = queryManager.createQuery(expression, Query.JCR_SQL2);
            QueryResult result = query.execute();
            for (Row row : JcrUtils.getRows(result)) {
                javax.jcr.Node node = row.getNode();
                pages.put(node.getPath(), node.getIdentifier());
            }
        } catch (RepositoryException e) {
            logger.error("Cannot fetch pages using template {}", template);
        }
        return pages;
    }

    @Override
    public HandlebarsTemplateDefinition getTemplateDefinition(String template) {
        try {
            Class<?> pageClass = Class.forName(template);
            if (templateDefinitions.containsKey(pageClass)) {
                return templateDefinitions.get(pageClass);
            } else {
                logger.warn("Cannot find template definitions for class {}", pageClass);
            }
        } catch (ClassNotFoundException e) {
            logger.error("Cannot find class {}", template);
        }
        throw new RuntimeException();
    }
}