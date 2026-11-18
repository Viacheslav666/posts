import type { Page } from '@playwright/test';

export class StoriesPage {
  constructor(private readonly page: Page) {}

  async gotoList(workspaceId: string) {
    await this.page.goto(`/app/${workspaceId}/stories`);
  }

  async gotoNew(workspaceId: string) {
    await this.page.goto(`/app/${workspaceId}/stories/new`);
  }

  async gotoBatch(workspaceId: string) {
    await this.page.goto(`/app/${workspaceId}/stories/batch`);
  }

  listHeading() {
    return this.page.getByRole('heading', { name: 'Отложенные истории' });
  }

  editorHeading() {
    return this.page.getByRole('heading', { name: 'Создать историю' });
  }

  editor() {
    return this.page.getByTestId('story-editor');
  }

  channelPicker() {
    return this.page.getByTestId('story-channel-picker');
  }

  unsupportedNetworks() {
    return this.page.getByTestId('story-unsupported-networks');
  }

  igAbsentMarker() {
    return this.page.getByTestId('story-ig-absent');
  }

  mediaBlock() {
    return this.page.getByTestId('story-media-block');
  }

  ttlSelect() {
    return this.page.getByTestId('story-ttl');
  }

  linkText() {
    return this.page.getByTestId('story-link-text');
  }

  linkUrl() {
    return this.page.getByTestId('story-link-url');
  }

  scheduleButton() {
    return this.page.getByTestId('story-schedule');
  }

  saveDraftButton() {
    return this.page.getByTestId('story-save-draft');
  }

  adForm() {
    return this.page.getByTestId('ad-marking-form');
  }

  createButton() {
    return this.page.getByTestId('stories-create');
  }
}
