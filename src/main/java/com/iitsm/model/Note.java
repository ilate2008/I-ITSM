package com.iitsm.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public record Note(LocalDateTime at, String by, String kind, String text) implements Serializable { }
