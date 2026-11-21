package com.iitsm.model;

import java.io.Serializable;

public record RelatedIncident(String incidentId, double score) implements Serializable { }
