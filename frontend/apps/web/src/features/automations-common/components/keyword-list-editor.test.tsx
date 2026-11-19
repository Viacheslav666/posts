import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { renderWithProviders } from '@/test/render';

import { KeywordListEditor } from './keyword-list-editor';

describe('KeywordListEditor', () => {
  it('adds chips from comma-separated paste and toggles match mode', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const onMatchModeChange = vi.fn();

    renderWithProviders(
      <KeywordListEditor
        value={['уже']}
        onChange={onChange}
        matchMode="whole_word"
        onMatchModeChange={onMatchModeChange}
        dictionaries={[{ id: 'd1', name: 'Спам', keywords: ['казино', 'ставки'] }]}
      />,
    );

    expect(screen.getByTestId('keyword-count')).toHaveTextContent('1');
    await user.type(screen.getByPlaceholderText(/Слово или список/i), 'бот, спам');
    await user.click(screen.getByRole('button', { name: 'Добавить' }));
    expect(onChange).toHaveBeenCalledWith(['уже', 'бот', 'спам']);

    await user.click(screen.getByTestId('keyword-match-substring'));
    expect(onMatchModeChange).toHaveBeenCalledWith('substring');

    await user.click(screen.getByTestId('keyword-import-d1'));
    expect(onChange).toHaveBeenCalledWith(['уже', 'казино', 'ставки']);
  });

  it('removes a chip', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    renderWithProviders(
      <KeywordListEditor
        value={['удали']}
        onChange={onChange}
        matchMode="substring"
        onMatchModeChange={vi.fn()}
      />,
    );
    await user.click(screen.getByLabelText(/Удалить «удали»/i));
    expect(onChange).toHaveBeenCalledWith([]);
  });
});
