package com.bbh.itss.beadle.protech.config

import org.springframework.mock.env.MockEnvironment
import org.yaml.snakeyaml.Yaml
import spock.lang.Specification

import static groovy.io.FileType.FILES

class OpenShiftManifestsSpec extends Specification {

    static final File MANIFESTS = new File('deploy/openshift/beadle-protech')

    def "the Deployment reads the ConfigMap and the secrets of Beadle-ProTech, the ProTech account optional"() {
        when:
        List<Map> sources = deployment().spec.template.spec.containers.first().envFrom

        then:
        sources*.configMapRef.findAll()*.name == ['beadle-protech-config']
        sources*.secretRef.findAll()*.name == ['beadle-protech-db', 'beadle-protech-services', 'beadle-protech-account']
        sources*.secretRef.findAll().findAll { it.optional }*.name == ['beadle-protech-account']
    }

    def "the start refused for a blank PROTECH_URL names the secret the Deployment reads it from"() {
        given:
        String account = deployment().spec.template.spec.containers.first().envFrom
                .find { it.secretRef?.optional }.secretRef.name

        when:
        RealProTechConfiguration.requireRealProTech(new MockEnvironment())

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("PROTECH_PASSWORD in the secret $account,")
    }

    def "no manifest calls ProTech ServiceNow, a word kept for the product and its REST API: #file"() {
        expect:
        !(file.text =~ /(?i)servicenow/)

        where:
        file << manifests()
    }

    private static Map deployment() {
        new Yaml().load(new File(MANIFESTS, 'base/deployment.yaml').text) as Map
    }

    private static List<File> manifests() {
        List<File> files = []
        MANIFESTS.eachFileRecurse(FILES) { files << it }
        assert files.size() >= 8
        files.sort()
    }
}
