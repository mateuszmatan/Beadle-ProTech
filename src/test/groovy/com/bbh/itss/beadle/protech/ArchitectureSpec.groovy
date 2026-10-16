package com.bbh.itss.beadle.protech

import com.bbh.itss.beadle.protech.support.ArchitectureSpecification

class ArchitectureSpec extends ArchitectureSpecification {

    @Override
    Class<?> application() {
        BeadleProTechApplication
    }

    @Override
    List<String> httpAdapters() {
        ['com.bbh.itss.beadle.protech.adapter.out.protech..', 'com.bbh.itss.beadle.protech.adapter.out.gui..']
    }
}
