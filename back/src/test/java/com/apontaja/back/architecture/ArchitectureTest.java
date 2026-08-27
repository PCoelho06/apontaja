package com.apontaja.back.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Vérifie mécaniquement le graphe de dépendances autorisées entre domaines métier et les règles
 * de couches définis au §2 du fichier de contexte (Phase 0, étape 4).
 *
 * <p><b>Choix d'implémentation</b> : utilise le module cœur ArchUnit ({@code archunit}, pas
 * {@code archunit-junit5}) piloté par de simples {@code @TestFactory}/{@code @BeforeAll} JUnit
 * Jupiter, pour ne pas coupler ce module de test à une version précise du moteur JUnit — voir le
 * commentaire dans {@code pom.xml}.
 *
 * <p><b>Portée de ces règles</b> : le graphe de dépendances entre domaines et l'isolation des
 * couches (web → application → domain, infrastructure → domain). La discipline "DTO obligatoire
 * aux frontières" (§2) n'est que partiellement couverte ici : en interdisant tout accès externe
 * aux packages {@code .domain}/{@code .web}/{@code .infrastructure} d'un domaine, ces règles
 * empêchent bien qu'un autre domaine ou la couche web n'accède directement aux entités JPA — mais
 * elles ne vérifient pas que les classes du package {@code .application} exposées sont
 * effectivement des DTO (convention à faire respecter en revue de code, pas encore automatisable
 * proprement sans convention de nommage établie).
 *
 * <p><b>Non exécuté par Claude</b> : écrit sans accès à `mvn` ni réseau. À valider par un
 * {@code mvn clean verify} local. Comme le projet ne contient pour l'instant que des
 * {@code package-info.java} (aucune classe métier), toutes ces règles passent trivialement
 * (rien ne les viole) — elles ne prennent tout leur sens qu'à partir du code métier des phases
 * suivantes. Pour vérifier qu'une règle détecterait bien une violation, on peut temporairement
 * ajouter une classe qui enfreint volontairement le graphe (ex. une classe dans
 * {@code account.application} qui référence {@code salon.domain}), lancer les tests, constater
 * l'échec, puis la supprimer.
 */
class ArchitectureTest {

    private static final String BASE_PACKAGE = "com.apontaja.back";

    /** Les 8 domaines métier définis au §2 — dans cet ordre, du plus racine au plus dépendant. */
    private static final List<String> DOMAINS = List.of(
            "account", "organization", "salon", "resource", "service", "customer", "appointment", "audit"
    );

    /**
     * Pour chaque couche, les couches du <b>même domaine</b> vers lesquelles elle ne doit jamais
     * dépendre. Traduit la règle de couches du §2 : {@code web → application → domain},
     * {@code infrastructure → domain}, jamais l'inverse.
     */
    private static final Map<String, List<String>> LAYER_FORBIDDEN_DEPENDENCIES = new LinkedHashMap<>();

    static {
        LAYER_FORBIDDEN_DEPENDENCIES.put("web", List.of("domain", "infrastructure"));
        LAYER_FORBIDDEN_DEPENDENCIES.put("application", List.of("web", "infrastructure"));
        LAYER_FORBIDDEN_DEPENDENCIES.put("domain", List.of("web", "application", "infrastructure"));
        LAYER_FORBIDDEN_DEPENDENCIES.put("infrastructure", List.of("web", "application"));
    }

    /**
     * Graphe de dépendances autorisées entre domaines (§2) : pour chaque domaine <b>cible</b>,
     * l'ensemble des domaines <b>source</b> autorisés à dépendre de sa couche
     * {@code .application} (seule couche exposée en dehors du domaine — voir
     * {@link #onlyApplicationLayerIsExposedAcrossDomains()}).
     *
     * <p>Transcription littérale de : {@code account (racine) → organization → salon → resource
     * → service ; customer → account ; appointment → salon, resource, service, customer (sommet,
     * rien n'en dépend) ; audit est un utilitaire transverse que tous les domaines peuvent
     * appeler en écriture, sans dépendre de personne.}
     *
     * <p>Lecture d'une entrée {@code cible -> [sources]} : "les domaines listés en source peuvent
     * dépendre de {@code cible.application}". Par exemple {@code account -> [organization,
     * customer]} traduit les deux arêtes {@code organization → account} et
     * {@code customer → account} du graphe.
     */
    private static final Map<String, List<String>> ALLOWED_DEPENDENTS = new LinkedHashMap<>();

    static {
        ALLOWED_DEPENDENTS.put("account", List.of("organization", "customer"));
        ALLOWED_DEPENDENTS.put("organization", List.of("salon"));
        ALLOWED_DEPENDENTS.put("salon", List.of("resource", "appointment"));
        ALLOWED_DEPENDENTS.put("resource", List.of("service", "appointment"));
        ALLOWED_DEPENDENTS.put("service", List.of("appointment"));
        ALLOWED_DEPENDENTS.put("customer", List.of("appointment"));
        ALLOWED_DEPENDENTS.put("appointment", List.of()); // sommet du graphe, rien n'en dépend
        ALLOWED_DEPENDENTS.put("audit", DOMAINS.stream()
                .filter(d -> !d.equals("audit"))
                .toList()); // utilitaire transverse, appelable en écriture par tous les domaines
    }

    private static JavaClasses importedClasses;

    @BeforeAll
    static void importClasses() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE_PACKAGE);
    }

    private static String domainPackage(String domain) {
        return BASE_PACKAGE + "." + domain + "..";
    }

    private static String layerPackage(String domain, String layer) {
        return BASE_PACKAGE + "." + domain + "." + layer + "..";
    }

    /**
     * Isolation des couches : à l'intérieur d'un même domaine, chaque couche ne doit dépendre que
     * de ce que la règle de couches du §2 autorise.
     */
    @TestFactory
    Stream<DynamicTest> layerIsolationWithinEachDomain() {
        return DOMAINS.stream().flatMap(domain ->
                LAYER_FORBIDDEN_DEPENDENCIES.entrySet().stream().map(entry -> {
                    String layer = entry.getKey();
                    List<String> forbiddenLayers = entry.getValue();
                    String testName = "domaine '%s' : la couche '%s' ne doit pas dépendre de %s"
                            .formatted(domain, layer, forbiddenLayers);

                    return DynamicTest.dynamicTest(testName, () -> {
                        String[] forbiddenPackages = forbiddenLayers.stream()
                                .map(forbiddenLayer -> layerPackage(domain, forbiddenLayer))
                                .toArray(String[]::new);

                        ArchRule rule = noClasses()
                                .that().resideInAPackage(layerPackage(domain, layer))
                                .should().dependOnClassesThat().resideInAnyPackage(forbiddenPackages)
                                .because("Règle de couches (§2) : web → application → domain, "
                                        + "infrastructure → domain, jamais l'inverse.");

                        rule.check(importedClasses);
                    });
                })
        );
    }

    /**
     * Confidentialité inter-domaines : seule la couche {@code .application} d'un domaine peut
     * être dépendue depuis l'extérieur de ce domaine. Ses entités JPA ({@code .domain}), ses
     * controllers ({@code .web}) et son infrastructure ({@code .infrastructure}) ne doivent
     * jamais fuiter vers un autre domaine — corollaire direct de la règle "DTO obligatoires aux
     * frontières inter-domaines, jamais un domaine n'expose ses entités JPA à un autre" (§2).
     */
    @TestFactory
    Stream<DynamicTest> onlyApplicationLayerIsExposedAcrossDomains() {
        return DOMAINS.stream().map(domain -> {
            String testName = ("domaine '%s' : seule '.application' peut être dépendue depuis l'extérieur "
                    + "(jamais '.domain', '.web' ni '.infrastructure')").formatted(domain);

            return DynamicTest.dynamicTest(testName, () -> {
                ArchRule rule = noClasses()
                        .that().resideOutsideOfPackage(domainPackage(domain))
                        .should().dependOnClassesThat().resideInAnyPackage(
                                layerPackage(domain, "domain"),
                                layerPackage(domain, "web"),
                                layerPackage(domain, "infrastructure")
                        )
                        .because("Un domaine n'expose jamais ses entités JPA, ses controllers ni son "
                                + "infrastructure à un autre domaine (§2).");

                rule.check(importedClasses);
            });
        });
    }

    /**
     * Graphe de dépendances autorisées entre domaines (§2) : pour chaque domaine cible, seuls les
     * domaines listés dans {@link #ALLOWED_DEPENDENTS} (et le domaine cible lui-même) peuvent
     * dépendre de sa couche {@code .application}.
     */
    @TestFactory
    Stream<DynamicTest> crossDomainDependencyGraph() {
        return DOMAINS.stream().map(target -> {
            List<String> allowedSources = ALLOWED_DEPENDENTS.getOrDefault(target, List.of());
            String testName = "domaine '%s' : seuls %s (et '%s' lui-même) peuvent dépendre de '.application'"
                    .formatted(target, allowedSources.isEmpty() ? "aucun autre domaine" : allowedSources, target);

            return DynamicTest.dynamicTest(testName, () -> {
                List<String> exemptDomainPackages = new ArrayList<>();
                exemptDomainPackages.add(domainPackage(target));
                allowedSources.forEach(source -> exemptDomainPackages.add(domainPackage(source)));

                ArchRule rule = noClasses()
                        .that().resideOutsideOfPackages(exemptDomainPackages.toArray(String[]::new))
                        .should().dependOnClassesThat().resideInAPackage(layerPackage(target, "application"))
                        .because("Graphe de dépendances autorisées entre domaines métier (§2 du fichier "
                                + "de contexte) — vérifié mécaniquement ici, ne pas rouvrir sans mise à "
                                + "jour explicite du fichier de contexte.");

                rule.check(importedClasses);
            });
        });
    }
}
