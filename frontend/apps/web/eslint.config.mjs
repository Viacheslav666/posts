import { dirname } from 'path';
import { fileURLToPath } from 'url';
import { FlatCompat } from '@eslint/eslintrc';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const compat = new FlatCompat({
  baseDirectory: __dirname,
});

const eslintConfig = [
  ...compat.extends('next/core-web-vitals'),
  {
    files: ['src/**/*.{ts,tsx}'],
    rules: {
      // Catch raw English post/publication status enums passed as badge labels.
      'no-restricted-syntax': [
        'error',
        {
          selector:
            "JSXOpeningElement[name.name='PostStatusBadge'] > JSXAttribute[name.name='label'][value.expression.type='MemberExpression'][value.expression.property.name=/^(status|state)$/]",
          message:
            'Не передавайте сырой status/state в label PostStatusBadge — используйте русскую метку (postStatusLabelRu / t(`status.*`)).',
        },
      ],
    },
  },
];

export default eslintConfig;
