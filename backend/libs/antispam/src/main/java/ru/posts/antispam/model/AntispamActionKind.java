package ru.posts.antispam.model;

/** Actions produced by {@link ru.posts.antispam.engine.AntispamEngine}. */
public enum AntispamActionKind {
    FLAG,
    REPLY,
    DELETE,
    BAN
}
