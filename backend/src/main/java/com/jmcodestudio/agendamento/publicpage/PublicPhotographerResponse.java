package com.jmcodestudio.agendamento.publicpage;

/** available=false significa "agenda temporariamente indisponível" (CA-030), não 404. */
public record PublicPhotographerResponse(String name, String timezone, boolean available) {
}
