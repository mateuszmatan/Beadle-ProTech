package com.bbh.itss.beadle.protech.config

import org.yaml.snakeyaml.Yaml
import spock.lang.Specification

import static groovy.io.FileType.FILES

class OpenShiftManifestsSpec extends Specification {

    static final File MANIFESTS = new File('deploy/openshift/beadle-protech')

    def "one pod reads the ConfigMap, the database and ProTech secrets of Beadle-ProTech and the token all Beadle services share"() {
        when:
        Map spec = manifest('base/deployment.yaml').spec
        List<Map> sources = spec.template.spec.containers.first().envFrom

        then:
        spec.replicas == 1
        sources*.configMapRef.findAll()*.name == ['beadle-protech-config']
        sources*.secretRef.findAll()*.name == ['beadle-protech-db', 'beadle-services', 'beadle-protech-account']
        sources.every { !it.secretRef?.optional }
        manifest('base/service.yaml').spec.ports*.port == [8083]
        manifest('components/webhook-route/route.yaml').spec.path == '/api/protech/webhook'
    }

    def "the base ConfigMap holds only what differs from application.yml and each overlay names its profile: #profile"() {
        expect:
        literals('base') == ['BEADLE_GUI_URL=http://beadle:8081']
        literals("overlays/$profile") == ["SPRING_PROFILES_ACTIVE=$profile".toString()]

        where:
        profile << ['rd', 'qc', 'prod']
    }

    def "no manifest calls ProTech ServiceNow, a word kept for the product and its REST API: #file.name"() {
        expect:
        !(file.text =~ /(?i)servicenow/)

        where:
        file << manifests()
    }

    private static Map manifest(String path) {
        new Yaml().load(new File(MANIFESTS, path).text) as Map
    }

    private static List<String> literals(String folder) {
        manifest("$folder/kustomization.yaml").configMapGenerator.first().literals as List<String>
    }

    private static List<File> manifests() {
        List<File> files = []
        MANIFESTS.eachFileRecurse(FILES) { files << it }
        assert files.size() >= 8
        files.sort()
    }
}
