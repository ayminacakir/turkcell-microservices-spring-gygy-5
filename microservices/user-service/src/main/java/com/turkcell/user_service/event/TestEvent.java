package com.turkcell.user_service.event;
import java.util.UUID;


public record TestEvent(String message, UUID productId) {}