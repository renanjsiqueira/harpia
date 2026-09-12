package dev.harpia.target.javaspring.transformer;

import dev.harpia.application.ApplicationEntity;
import dev.harpia.application.ApplicationField;
import dev.harpia.target.javaspring.JavaLayout;
import dev.harpia.target.javaspring.JavaSpringContext;
import dev.harpia.target.javaspring.mapping.JavaDefaultValueMapper;
import dev.harpia.target.javaspring.mapping.JavaTypeMapper;
import dev.harpia.target.javaspring.mapping.SqlConstraintNames;
import dev.harpia.target.javaspring.mapping.SpringPersistenceMapper;
import dev.harpia.target.javaspring.mapping.SpringValidationMapper;
import dev.harpia.target.javaspring.model.JavaAnnotationModel;
import dev.harpia.target.javaspring.model.JavaConstructorModel;
import dev.harpia.target.javaspring.model.JavaFieldModel;
import dev.harpia.target.javaspring.model.JavaImportModel;
import dev.harpia.target.javaspring.model.JavaMethodModel;
import dev.harpia.target.javaspring.model.JavaParameterModel;
import dev.harpia.target.javaspring.model.JavaSourceFile;
import dev.harpia.target.javaspring.model.JavaTypeModel;
import dev.harpia.target.javaspring.model.JavaTypeRef;
import dev.harpia.target.javaspring.model.JavaVisibility;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Translates a persistent Application IR entity into a complete JPA Java model. */
public final class JavaSpringEntityTransformer {

    private final SpringValidationMapper validation = new SpringValidationMapper();
    private final SpringPersistenceMapper persistence = new SpringPersistenceMapper();
    private final JavaDefaultValueMapper defaults = new JavaDefaultValueMapper();

    public JavaSourceFile transform(JavaSpringContext context, ApplicationEntity entity) {
        List<JavaFieldModel> fields = new ArrayList<>();
        List<JavaMethodModel> methods = new ArrayList<>();
        List<JavaImportModel> explicitImports = new ArrayList<>();
        for (ApplicationField field : entity.fields()) {
            List<JavaAnnotationModel> annotations = new ArrayList<>(validation.map(field));
            if (persistence.ownsColumn(field)) {
                annotations.addAll(persistence.fieldAnnotations(entity, field));
            }
            explicitImports.addAll(persistence.additionalImports(entity, field));
            String domain = context.layout().packageName(JavaLayout.DOMAIN);
            JavaTypeRef type = JavaTypeMapper.stored(field.type(), domain);
            field.elementType().ifPresent(element -> {
                // A collection cannot live in a column of the owner's row, so it gets a table of
                // its own, keyed back to the owner. The names match what the migration created.
                String table = entity.tableName() + "_" + field.columnName();
                explicitImports.add(new JavaImportModel("jakarta.persistence.JoinColumn"));
                if (element instanceof dev.harpia.application.ApplicationFieldType.Relationship
                        relationship) {
                    relationshipCollection(
                            entity, table, relationship, annotations, explicitImports);
                } else {
                    explicitImports.add(
                            new JavaImportModel("jakarta.persistence.CollectionTable"));
                    explicitImports.add(new JavaImportModel("jakarta.persistence.Column"));
                    annotations.add(JavaAnnotationModel.marker(
                            "jakarta.persistence.ElementCollection"));
                    annotations.add(JavaAnnotationModel.of(
                            "jakarta.persistence.CollectionTable",
                            new JavaAnnotationModel.Attribute("name", "\"" + table + "\""),
                            new JavaAnnotationModel.Attribute(
                                    "joinColumns",
                                    "@JoinColumn(name = \"" + entity.tableName() + "_id\")")));
                    annotations.add(JavaAnnotationModel.of(
                            "jakarta.persistence.Column",
                            new JavaAnnotationModel.Attribute(
                                    "name", "\"" + field.columnName() + "\"")));
                    if (element instanceof dev.harpia.application.ApplicationFieldType.EnumType) {
                        explicitImports.add(new JavaImportModel("jakarta.persistence.EnumType"));
                        annotations.add(JavaAnnotationModel.of(
                                "jakarta.persistence.Enumerated",
                                new JavaAnnotationModel.Attribute("value", "EnumType.STRING")));
                    }
                }
            });
            field.valueType().ifPresent(value -> {
                annotations.add(JavaAnnotationModel.marker("jakarta.persistence.Embedded"));
                // Hibernate would default each component to its own bare column name, which two
                // embedded values in one table would collide on. The prefix is the field that
                // holds the value, and it has to match the column the migration created.
                explicitImports.add(new JavaImportModel("jakarta.persistence.Column"));
                for (ApplicationField component : value.components()) {
                    annotations.add(JavaAnnotationModel.of(
                            "jakarta.persistence.AttributeOverride",
                            new JavaAnnotationModel.Attribute(
                                    "name", "\"" + component.name() + "\""),
                            new JavaAnnotationModel.Attribute(
                                    "column",
                                    "@Column(name = \"" + field.columnName() + "_"
                                            + component.columnName() + "\")")));
                }
            });
            field.enumTypeName().ifPresent(ignored -> {
                // Ordinals encode a position, which changes when the specification reorders its
                // values. The name is what the specification actually declared, so it is stored.
                explicitImports.add(new JavaImportModel("jakarta.persistence.EnumType"));
                annotations.add(JavaAnnotationModel.of(
                        "jakarta.persistence.Enumerated",
                        new JavaAnnotationModel.Attribute("value", "EnumType.STRING")));
            });
            // A collection that was never assigned is empty, not absent: adding to a null one
            // would fail on the first element the flow contributes.
            Optional<String> initial = field.elementType().isPresent()
                    ? Optional.of("new ArrayList<>()")
                    : defaults.map(field);
            field.elementType().ifPresent(ignored ->
                    explicitImports.add(new JavaImportModel("java.util.ArrayList")));
            fields.add(new JavaFieldModel(
                    field.name(),
                    type,
                    JavaVisibility.PRIVATE,
                    Set.of(),
                    annotations,
                    initial,
                    Optional.of(field.where())));
            methods.add(getter(field, type, JavaTypeMapper.map(field.type(), domain)));
            methods.add(setter(field, type));
        }

        String packageName = context.layout().packageName(JavaLayout.DOMAIN);
        JavaTypeModel type = new JavaTypeModel(
                JavaTypeModel.Kind.CLASS,
                packageName,
                entity.typeName(),
                JavaVisibility.PUBLIC,
                Set.of(),
                Optional.of("Persistent entity generated from the Harpia entity "
                        + entity.typeName() + "."),
                persistence.entityAnnotations(entity),
                explicitImports,
                List.of(),
                fields,
                // Public rather than protected: JPA accepts either, and the generated service
                // instantiates the entity from another package for `create ... from input`.
                List.of(new JavaConstructorModel(
                        JavaVisibility.PUBLIC,
                        List.of(),
                        List.of(),
                        List.of(),
                        Optional.of(entity.where()))),
                methods,
                Optional.of(entity.where()));
        return new JavaSourceFile(
                JavaLayout.sourcePath(context.layout().packagePath(JavaLayout.DOMAIN), entity.typeName()),
                type,
                Optional.of(entity.where()));
    }

    /** A non-owned collection is shared and keeps the target's lifecycle independent. */
    private static void relationshipCollection(
            ApplicationEntity entity,
            String table,
            dev.harpia.application.ApplicationFieldType.Relationship relationship,
            List<JavaAnnotationModel> annotations,
            List<JavaImportModel> imports) {
        String ownerColumn = entity.tableName() + "_id";
        String targetColumn = dev.harpia.application.SqlNaming.identifier(relationship.entity())
                + "_id";
        boolean dependent = relationship.lifecycle()
                == dev.harpia.application.ApplicationFieldType.RelationshipLifecycle.DEPENDENT;
        imports.add(new JavaImportModel("jakarta.persistence.FetchType"));
        imports.add(new JavaImportModel("jakarta.persistence.ForeignKey"));
        imports.add(new JavaImportModel("jakarta.persistence.JoinTable"));
        List<JavaAnnotationModel.Attribute> association = new ArrayList<>();
        association.add(new JavaAnnotationModel.Attribute(
                "fetch", "FetchType." + relationship.loading().name()));
        if (dependent) {
            imports.add(new JavaImportModel("jakarta.persistence.CascadeType"));
            association.add(new JavaAnnotationModel.Attribute("cascade", "CascadeType.ALL"));
            association.add(new JavaAnnotationModel.Attribute("orphanRemoval", "true"));
        }
        annotations.add(new JavaAnnotationModel(
                JavaTypeRef.of(dependent
                        ? "jakarta.persistence.OneToMany"
                        : "jakarta.persistence.ManyToMany"),
                association));
        annotations.add(JavaAnnotationModel.of(
                "jakarta.persistence.JoinTable",
                new JavaAnnotationModel.Attribute("name", "\"" + table + "\""),
                new JavaAnnotationModel.Attribute(
                        "joinColumns",
                        "@JoinColumn(name = \"" + ownerColumn
                                + "\", foreignKey = @ForeignKey(name = \""
                                + SqlConstraintNames.foreignKey(table, ownerColumn) + "\"))"),
                new JavaAnnotationModel.Attribute(
                        "inverseJoinColumns",
                        "@JoinColumn(name = \"" + targetColumn
                                + "\"" + (dependent ? ", unique = true" : "")
                                + ", foreignKey = @ForeignKey(name = \""
                                + SqlConstraintNames.foreignKey(table, targetColumn) + "\"))")));
    }

    /**
     * @param stored the type the field is held in, which JPA reads
     * @param exposed the type callers see, which says whether the value can be absent
     */
    private static JavaMethodModel getter(
            ApplicationField field, JavaTypeRef stored, JavaTypeRef exposed) {
        boolean optional = field.optionalType().isPresent();
        return new JavaMethodModel(
                accessor("get", field.name()),
                exposed,
                JavaVisibility.PUBLIC,
                Set.of(),
                List.of(),
                List.of(),
                List.of(optional
                        ? "return Optional.ofNullable(" + field.name() + ");"
                        : "return " + field.name() + ";"),
                Optional.of(field.where()));
    }

    private static JavaMethodModel setter(ApplicationField field, JavaTypeRef type) {
        return new JavaMethodModel(
                accessor("set", field.name()),
                JavaTypeRef.of("void"),
                JavaVisibility.PUBLIC,
                Set.of(),
                List.of(),
                List.of(new JavaParameterModel(field.name(), type)),
                List.of("this." + field.name() + " = " + field.name() + ";"),
                Optional.of(field.where()));
    }

    private static String accessor(String prefix, String fieldName) {
        return prefix + fieldName.substring(0, 1).toUpperCase(Locale.ROOT) + fieldName.substring(1);
    }
}
