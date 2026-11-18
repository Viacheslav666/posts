package ru.posts.content;

/** Renders canonical HTML/text for a target social network and validates limits. */
public interface ContentRenderer {

    ContentRenderResult render(ContentRenderRequest request);
}
