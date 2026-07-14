package com.museotek.box.web.error;

import java.util.List;

public record ErrorEnvelope(String code, String message, List<String> details) {}
