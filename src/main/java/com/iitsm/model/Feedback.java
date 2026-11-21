package com.iitsm.model;

import java.io.Serializable;

public record Feedback(int rating, String comment) implements Serializable { }
