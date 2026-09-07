package dev.harpia.target.javaspring.mapping;

import dev.harpia.application.ApplicationEntity;
import dev.harpia.application.ApplicationField;
import dev.harpia.target.javaspring.PostgresTypes;
import dev.harpia.target.javaspring.model.JavaAnnotationModel;
import dev.harpia.target.javaspring.model.JavaAnnotationModel.Attribute;
import dev.harpia.target.javaspring.model.JavaImportModel;
import java.util.ArrayList;
import java.util.List;

/** Resolves persistence intent into JPA annotations before rendering starts. */
public final class SpringPersistenceMapper {

    public List<JavaAnnotationModel> entityAnnotations(ApplicationEntity entity) {
        return List.of(
                JavaAnnotationModel.marker("jakarta.persistence.Entity"),
                JavaAnnotationModel.of(
                        "jakarta.persistence.Table",
                        new Attribute("name", quote(entity.tableName()))));
    }

    public List<JavaAnnotationModel> fieldAnnotations(
            ApplicationEntity entity, ApplicationField field) {
        boolean identifier = field.equals(entity.idField());
        List<JavaAnnotationModel> annotations = new ArrayList<>();
        if (identifier) {
            annotations.add(JavaAnnotationModel.marker("jakarta.persistence.Id"));
            annotations.add(JavaAnnotationModel.of(
                    "jakarta.persistence.GeneratedValue",
                    new Attribute("strategy", "GenerationType.UUID")));
        }

        List<Attribute> attributes = new ArrayList<>();
        attributes.add(new Attribute("name", quote(field.columnName())));
        if (field.required() || identifier) {
            attributes.add(new Attribute("nullable", "false"));
        }
        if (field.unique()) {
            attributes.add(new Attribute("unique", "true"));
        }
        if (identifier) {
            attributes.add(new Attribute("updatable", "false"));
        }
        PostgresTypes.columnAttributes(field.type())
                .map(SpringPersistenceMapper::attribute)
                .ifPresent(attributes::add);
        annotations.add(new JavaAnnotationModel(
                dev.harpia.target.javaspring.model.JavaTypeRef.of("jakarta.persistence.Column"),
                attributes));
        return List.copyOf(annotations);
    }

    public List<JavaImportModel> additionalImports(
            ApplicationEntity entity, ApplicationField field) {
        return field.equals(entity.idField())
                ? List.of(new JavaImportModel("jakarta.persistence.GenerationType"))
                : List.of();
    }

    private static Attribute attribute(String source) {
        int separator = source.indexOf(" = ");
        if (separator < 1) {
            throw new IllegalArgumentException("invalid persistence attribute: " + source);
        }
        return new Attribute(source.substring(0, separator), source.substring(separator + 3));
    }

    private static String quote(String value) {
        return "\"" + value + "\"";
    }
}
