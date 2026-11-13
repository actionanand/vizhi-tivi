/** Human-facing channel language names. Stored ISO codes remain unchanged. */
export function displayLanguage(code: string, locale: 'en' | 'ta' | 'sa' = 'en'): string {
  const normalized = code.toLowerCase().trim().replaceAll('_', '-');
  const unknown = { en: 'Unknown language', ta: 'தெரியாத மொழி', sa: 'अज्ञातभाषा' };
  if (!normalized || normalized === 'und' || normalized === 'unknown') return unknown[locale];
  // Intl.DisplayNames uses the browser's CLDR language data, including Indian and Sri Lankan languages.
  try {
    const display = new Intl.DisplayNames([locale, 'en'], { type: 'language', fallback: 'none' }).of(normalized);
    if (display && display.toLowerCase() !== normalized) {
      return display.charAt(0).toLocaleUpperCase(locale) + display.slice(1);
    }
  } catch {
    // Browser without DisplayNames or unsupported language code: use conservative fallback.
  }
  const english: Record<string, string> = {
    ta: 'Tamil', en: 'English', hi: 'Hindi', ml: 'Malayalam',
    te: 'Telugu', kn: 'Kannada', si: 'Sinhala', sa: 'Sanskrit',
    bn: 'Bengali', mr: 'Marathi', pa: 'Punjabi', gu: 'Gujarati',
    ur: 'Urdu', or: 'Odia', as: 'Assamese', ks: 'Kashmiri',
    fr: 'French', de: 'German', es: 'Spanish', ar: 'Arabic',
  };
  return english[normalized] ?? unknown[locale];
}
