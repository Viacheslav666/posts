import type { Page } from '@playwright/test';

export class OrdPage {
  constructor(private readonly page: Page) {}

  async goto(workspaceId: string): Promise<void> {
    await this.page.goto(`/app/${workspaceId}/ord`);
  }

  heading() {
    return this.page.getByRole('heading', { name: 'Реклама / ОРД' });
  }

  connectionForm() {
    return this.page.getByTestId('ord-connection-form');
  }

  apiKey() {
    return this.page.getByTestId('ord-api-key');
  }

  contractors() {
    return this.page.getByTestId('ord-contractors');
  }

  reportsTab() {
    return this.page.getByTestId('ord-tab-reports');
  }

  async openReportsTab(): Promise<void> {
    await this.reportsTab().click();
  }

  reportsPanel() {
    return this.page.getByTestId('ord-reports-panel');
  }

  reportsTable() {
    return this.page.getByTestId('ord-reports-table');
  }

  runReportButton() {
    return this.page.getByTestId('ord-reports-run');
  }

  exportCsvButton() {
    return this.page.getByTestId('ord-reports-export-csv');
  }

  exportXlsxButton() {
    return this.page.getByTestId('ord-reports-export-xlsx');
  }

  eridInput() {
    return this.page.getByTestId('ord-reports-erid-input');
  }

  eridSubmit() {
    return this.page.getByTestId('ord-reports-erid-submit');
  }

  eridResult() {
    return this.page.getByTestId('ord-reports-erid-result');
  }
}
