package com.iitsm.model;

import java.time.Duration;

public record SlaTarget(Duration response, Duration resolution) { }
