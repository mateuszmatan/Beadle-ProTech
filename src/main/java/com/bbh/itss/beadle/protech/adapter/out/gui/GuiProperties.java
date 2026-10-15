package com.bbh.itss.beadle.protech.adapter.out.gui;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Where Beadle-GUI takes the callbacks of Beadle-ProTech ({@code BEADLE_GUI_URL}). */
@ConfigurationProperties("beadle.gui")
public record GuiProperties(@DefaultValue("http://localhost:8081") String url) {
}
