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

    /** A value is embedded and a collection has its own table, so neither owns a column here. */
    public boolean ownsColumn(ApplicationField field) {
        return field.valueType().isEmpty() && field.elementType().isEmpty();
    }

    public List<JavaAnnotationModel> fieldAnnotations(
            ApplicationEntity entity, ApplicationField field) {
        if (field.relationship().isPresent()) {
            return relationshipAnnotations(entity, field);
        }
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
        // A declared type is stored by name, so its column carries a length rather than one of
        // the scalar shapes Postgres has a type for.
        if (field.reference().isPresent()) {
            // The identity's own shape decides the column; the foreign key lives in the migration.
            PostgresTypes.columnAttributes(field.reference().orElseThrow().idType())
                    .map(SpringPersistenceMapper::attribute)
                    .ifPresent(attributes::add);
        } else if (field.enumTypeName().isPresent()) {
            attributes.add(new Attribute("length", "64"));
        } else {
            PostgresTypes.columnAttributes(field.scalarType())
                    .map(SpringPersistenceMapper::attribute)
                    .ifPresent(attributes::add);
        }
        annotations.add(new JavaAnnotationModel(
                dev.harpia.target.javaspring.model.JavaTypeRef.of("jakarta.persistence.Column"),
                attributes));
        return List.copyOf(annotations);
    }

    public List<JavaImportModel> additionalImports(
            ApplicationEntity entity, ApplicationField field) {
        if (field.relationship().isPresent()) {
            return List.of(
                    new JavaImportModel("jakarta.persistence.FetchType"),
                    new JavaImportModel("jakarta.persistence.ForeignKey"));
        }
        return field.equals(entity.idField())
                ? List.of(new JavaImportModel("jakarta.persistence.GenerationType"))
                : List.of();
    }

    private static List<JavaAnnotationModel> relationshipAnnotations(
            ApplicationEntity entity, ApplicationField field) {
        dev.harpia.application.ApplicationFieldType.Relationship relationship =
                field.relationship().orElseThrow();
        List<Attribute> join = new ArrayList<>();
        join.add(new Attribute("name", quote(field.columnName())));
        join.add(new Attribute("nullable", Boolean.toString(!field.required())));
        if (field.unique()) {
            join.add(new Attribute("unique", "true"));
        }
        join.add(new Attribute(
                "foreignKey",
                "@ForeignKey(name = \""
                        + SqlConstraintNames.foreignKey(entity.tableName(), field.columnName())
                        + "\")"));
        return List.of(
                JavaAnnotationModel.of(
                        "jakarta.persistence.ManyToOne",
                        new Attribute("fetch", "FetchType." + relationship.loading().name()),
                        new Attribute("optional", Boolean.toString(!field.required()))),
                new JavaAnnotationModel(
                        dev.harpia.target.javaspring.model.JavaTypeRef.of(
                                "jakarta.persistence.JoinColumn"),
                        join));
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
