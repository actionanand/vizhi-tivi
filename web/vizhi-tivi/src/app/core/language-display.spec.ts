import { displayLanguage } from './language-display';

describe('Localized channel language labels', () => {
  it('shows readable names rather than raw ISO codes', () => {
    expect(displayLanguage('ta', 'en')).toBe('Tamil');
    expect(displayLanguage('en', 'en')).toBe('English');
    expect(displayLanguage('hi', 'en')).toBe('Hindi');
    expect(displayLanguage('ml', 'en')).toBe('Malayalam');
    expect(displayLanguage('te', 'en')).toBe('Telugu');
    expect(displayLanguage('kn', 'en')).toBe('Kannada');
    expect(displayLanguage('si', 'en')).toBe('Sinhala');
  });
  it('does not render undecipherable codes as if they were language names', () => {
    expect(displayLanguage('und', 'en')).toBe('Unknown language');
    expect(displayLanguage('')).toBe('Unknown language');
    expect(displayLanguage('qqq')).toBe('Unknown language');
  });
  it('uses the interface locale when requested', () => {
    expect(displayLanguage('ta', 'ta')).toBe('தமிழ்');
    expect(displayLanguage('en', 'en')).toBe('English');
  });
});
