const PAIRS: Array<[string, string]> = [
  ['Deutschland', 'Germany'],
  ['Österreich', 'Austria'],
  ['Australien', 'Australia'],
  ['Belgien', 'Belgium'],
  ['Brasilien', 'Brazil'],
  ['Kanada', 'Canada'],
  ['China', 'China'],
  ['Dänemark', 'Denmark'],
  ['Finnland', 'Finland'],
  ['Frankreich', 'France'],
  ['Großbritannien', 'United Kingdom'],
  ['Italien', 'Italy'],
  ['Japan', 'Japan'],
  ['Mexiko', 'Mexico'],
  ['Monaco', 'Monaco'],
  ['Niederlande', 'Netherlands'],
  ['Neuseeland', 'New Zealand'],
  ['Polen', 'Poland'],
  ['Südafrika', 'South Africa'],
  ['Spanien', 'Spain'],
  ['Thailand', 'Thailand'],
  ['USA', 'United States'],
  ['Schweiz', 'Switzerland'],
  ['Argentinien', 'Argentina'],
  ['Indien', 'India'],
  ['Russland', 'Russia'],
  ['Singapur', 'Singapore'],
  ['Bahrain', 'Bahrain'],
  ['Saudi-Arabien', 'Saudi Arabia'],
  ['Aserbaidschan', 'Azerbaijan'],
  ['Ungarn', 'Hungary'],
  ['Katar', 'Qatar'],
  ['Vereinigte Arabische Emirate', 'United Arab Emirates'],
]

const DE_TO_EN = new Map<string, string>()
const EN_TO_DE = new Map<string, string>()
for (const [de, en] of PAIRS) {
  DE_TO_EN.set(de, en)
  EN_TO_DE.set(en, de)
}
EN_TO_DE.set('Great Britain', 'Großbritannien')
EN_TO_DE.set('UK', 'Großbritannien')
EN_TO_DE.set('USA', 'USA')
EN_TO_DE.set('United States of America', 'USA')
DE_TO_EN.set('Vereinigtes Königreich', 'United Kingdom')

export function countryLabel(name: string | null | undefined, locale: string): string {
  if (!name) return ''
  if (locale === 'en') return DE_TO_EN.get(name) ?? name
  return EN_TO_DE.get(name) ?? name
}
