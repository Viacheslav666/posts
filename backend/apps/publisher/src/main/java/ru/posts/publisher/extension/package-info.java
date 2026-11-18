/**
 * Pipeline extension points for BE-15 (ORD/erid), BE-16 (stories), BE-17 (grabber ingress).
 *
 * <ul>
 *   <li>{@link ru.posts.publisher.extension.PrePublishHook} — transform/gate content before the
 *       network call (erid marker, fail-closed without erid).
 *   <li>{@code content_kind=story} is branched inside {@code PublicationProcessor} via {@code
 *       SocialAdapter#publishStory} without a separate Deployment.
 *   <li>Grabber/RSS (BE-17) must create posts through the domain schedule API so publications flow
 *       only through this pipeline (no bypass of idempotency).
 * </ul>
 */
package ru.posts.publisher.extension;
