import { getTranslations } from 'next-intl/server';
import Link from 'next/link';
import type { ReactNode } from 'react';

import { hasLegalValue, LEGAL_EDITION_DATE } from '@/shared/config/legal';

import { LegalRequisitesView, loadLegalRequisiteRows } from './legal-requisites';

const linkClass = 'font-medium text-foreground underline underline-offset-4';

export async function PersonalDataConsent() {
  const t = await getTranslations('legal');
  const { requisites, rows } = await loadLegalRequisiteRows();

  return (
    <main id="main-content" className="mx-auto w-full max-w-3xl px-4 pb-20 pt-28 sm:px-6 lg:px-8">
      <h1 className="font-display text-3xl font-bold tracking-tight text-foreground">
        {t('personalData.title')}
      </h1>
      <p className="mt-3 text-sm text-muted-foreground" data-testid="consent-edition">
        Редакция от {LEGAL_EDITION_DATE}
      </p>
      <article className="mt-8 space-y-8 text-sm leading-relaxed text-muted-foreground">
        <Section title="1. Кто дает согласие">
          <p>
            Регистрируясь в сервисе Posts или продолжая его использование, пользователь свободно, своей
            волей и в своем интересе дает согласие на обработку персональных данных оператору.
          </p>
          <p>
            Оператор: физическое лицо, применяющее налог на профессиональный доход (самозанятый).
            Реквизиты оператора указаны в разделе 6 и совпадают с реквизитами на страницах оферты и
            политики конфиденциальности.
          </p>
        </Section>

        <Section title="2. На что дается согласие">
          <p>
            Согласие соответствует{' '}
            <DocLink href="/legal/privacy">политике конфиденциальности</DocLink> и дается на обработку
            следующих данных:
          </p>
          <ul className="list-disc space-y-2 pl-5">
            <li>адрес электронной почты;</li>
            <li>имя;</li>
            <li>данные профиля VK ID, если пользователь входит через VK;</li>
            <li>номер телефона, если пользователь его указал;</li>
            <li>токены подключенных аккаунтов социальных сетей;</li>
            <li>
              сведения о платеже. Оплату принимает ЮKassa. Данные банковской карты оператор не получает и
              не хранит;
            </li>
            <li>файлы cookie и сведения аналитики посещения сайта.</li>
          </ul>
          <p>Цели обработки:</p>
          <ul className="list-disc space-y-2 pl-5">
            <li>регистрация учетной записи и предоставление сервиса Posts;</li>
            <li>прием оплаты через ЮKassa и направление чека;</li>
            <li>поддержка и уведомления о работе сервиса и оплате.</li>
          </ul>
          <p>
            Действия с данными: сбор, запись, систематизация, накопление, хранение, уточнение,
            использование, передача лицам, названным в политике конфиденциальности, обезличивание,
            блокирование и удаление.
          </p>
        </Section>

        <Section title="3. Кому могут быть переданы данные">
          <p>В объеме, нужном для указанных целей, данные могут быть переданы:</p>
          <ul className="list-disc space-y-2 pl-5">
            <li>ЮKassa, для приема оплаты, без хранения номера карты у оператора;</li>
            <li>VK, для входа через VK ID и публикации;</li>
            <li>сервису электронной почты, через который оператор отправляет письма и чеки;</li>
            <li>хостингу на территории Российской Федерации, где размещены сервис и базы данных.</li>
          </ul>
          <p>
            Чек формируется в приложении «Мой налог» и направляется на электронную почту покупателя.
            Базы с персональными данными граждан Российской Федерации размещаются в Российской Федерации.
          </p>
        </Section>

        <Section title="4. Срок согласия">
          <p>
            Согласие действует до его отзыва. После окончания подписки данные хранятся 90 дней, затем
            удаляются или обезличиваются, если закон не требует хранить отдельные сведения дольше.
          </p>
          <p>
            Обработка, которая нужна для исполнения{' '}
            <DocLink href="/legal/offer">публичной оферты</DocLink> или для исполнения закона, может
            продолжаться на этих основаниях и после отзыва согласия.
          </p>
        </Section>

        <Section title="5. Как отозвать согласие">
          <p>
            Отзыв направляется на <ContactEmail email={requisites.email} />. В письме достаточно указать
            адрес почты учетной записи и просьбу прекратить обработку, основанную на согласии. Оператор
            прекращает такую обработку в срок, установленный 152-ФЗ.
          </p>
          <p>
            Пользователь также вправе запросить доступ к данным, их уточнение или удаление тем же
            способом. Порядок прав описан в политике конфиденциальности.
          </p>
        </Section>

        <Section title="6. Реквизиты оператора">
          <LegalRequisitesView rows={rows} className="mt-3" />
          <p>
            Запросы и отзыв согласия направляются на <ContactEmail email={requisites.email} />.
          </p>
          <p>Дата редакции согласия: {LEGAL_EDITION_DATE}.</p>
        </Section>
      </article>
    </main>
  );
}

function ContactEmail({ email }: { email: string }) {
  if (!hasLegalValue(email)) {
    return <>электронную почту оператора, указанную в реквизитах</>;
  }
  return (
    <a href={`mailto:${email}`} className={linkClass}>
      {email}
    </a>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="space-y-3">
      <h2 className="font-display text-xl font-semibold text-foreground">{title}</h2>
      <div className="space-y-3">{children}</div>
    </section>
  );
}

function DocLink({ href, children }: { href: string; children: ReactNode }) {
  return (
    <Link href={href} className={linkClass}>
      {children}
    </Link>
  );
}
