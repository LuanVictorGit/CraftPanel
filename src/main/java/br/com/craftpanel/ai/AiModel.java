package br.com.craftpanel.ai;

/** A chat model offered by a provider. */
public record AiModel(String id, String displayName, boolean thinking) {}
