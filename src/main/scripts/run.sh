#!/bin/sh
java -cp ".:lib/*:proline-swissknife-${pom.version}.jar" -Dlogback.configurationFile=logback.xml fr.edyp.proline.extraction.Main $@
