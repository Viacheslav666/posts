import { getTranslations } from 'next-intl/server';
import Link from 'next/link';
import type { ReactNode } from 'react';

import { formatRub, PLANS, priceForPeriod, YEARLY_DISCOUNT, type Plan } from '@/features/pricing/model/plans';
import { hasLegalValue } from '@/shared/config/legal';

import { LegalRequisitesView, loadLegalRequisiteRows } from './legal-requisites';

export const PUBLIC_OFFER_EDITION = '26 ноября 2026 г.';

const linkClass = 'font-medium text-foreground underline underline-offset-4';

export async function PublicOffer() {
  const t = await getTranslations('legal');
  const pricing = await getTranslations('pricing');
  const { requisites, rows } = await loadLegalRequisiteRows();
  const yearlyDiscountPercent = Math.round(YEARLY_DISCOUNT * 100);
  const proName = pricing('plans.pro.name');

  return (
    <main id="main-content" className="mx-auto w-full max-w-3xl px-4 pb-20 pt-28 sm:px-6 lg:px-8">
      <h1 className="font-display text-3xl font-bold tracking-tight text-foreground">{t('offer.title')}</h1>
      <p className="mt-3 text-sm text-muted-foreground" data-testid="offer-edition">
        Редакция от {PUBLIC_OFFER_EDITION}
      </p>
      <article className="mt-8 space-y-8 text-sm leading-relaxed text-muted-foreground">
        <OfferSection title="1. Общие положения">
          <p>
            Настоящий документ является публичной офертой физического лица, применяющего специальный
            налоговый режим «Налог на профессиональный доход» (самозанятого), далее Исполнитель, и
            содержит предложение заключить договор возмездного оказания услуг с любым дееспособным лицом,
            далее Заказчик.
          </p>
          <p>
            Оферта адресована неопределенному кругу лиц. Договор заключается в порядке статей 437 и 438
            Гражданского кодекса Российской Федерации. Имя, ИНН, город и контакты Исполнителя указаны в
            разделе «Реквизиты исполнителя».
          </p>
          <p>
            Сервис Posts предназначен для отложенного постинга в социальные сети: Заказчик готовит
            публикацию в личном кабинете, назначает время выхода, и сервис отправляет ее в подключенные
            каналы.
          </p>
        </OfferSection>

        <OfferSection title="2. Термины">
          <p>Сервис: сайт и личный кабинет Posts.</p>
          <p>Тариф: набор лимитов и цена доступа, опубликованные на странице тарифов.</p>
          <p>Личный кабинет: раздел сервиса, доступный Заказчику после регистрации.</p>
          <p>Оплаченный период: месяц или год, за который поступила оплата выбранного тарифа.</p>
        </OfferSection>

        <OfferSection title="3. Предмет договора">
          <p>
            Исполнитель предоставляет Заказчику доступ к сервису Posts (отложенный постинг в социальные
            сети) по выбранному тарифу, а Заказчик оплачивает этот доступ.
          </p>
          <p>
            В доступ входит подготовка, планирование и публикация материалов в социальных сетях, которые
            Заказчик подключает сам, в пределах лимитов тарифа.
          </p>
        </OfferSection>

        <OfferSection title="4. Акцепт оферты">
          <p>
            Акцептом оферты являются регистрация в сервисе и оплата выбранного тарифа. Договор считается
            заключенным с момента поступления оплаты.
          </p>
          <p>
            Оплата подтверждает, что Заказчик ознакомился с этой офертой и принимает ее условия.
            Регистрация без оплаты открывает только бесплатный тариф, если он указан на странице тарифов.
          </p>
        </OfferSection>

        <OfferSection title="5. Тарифы и стоимость">
          <p>
            Цены указаны в рублях Российской Федерации. Актуальный перечень размещен на{' '}
            <DocLink href="/pricing">странице тарифов</DocLink>. Стоимость на дату этой редакции:
          </p>
          <ul className="list-disc space-y-2 pl-5">
            {PLANS.map((plan) => (
              <li key={plan.id}>
                {pricing(`plans.${plan.nameKey}.name`)}: {tariffPrice(plan, pricing('freePrice'))}. Каналы:{' '}
                {plan.channels}. Места в команде: {plan.seats}. Посты: {plan.posts}. AI-кредиты:{' '}
                {plan.aiCredits}.
              </li>
            ))}
          </ul>
          <p>
            Оплатить можно за месяц или за год. Годовая цена равна стоимости 12 месяцев со скидкой{' '}
            {yearlyDiscountPercent}% и вносится одним платежом.
          </p>
          <p>
            Тариф «{proName}» можно подключить на 7 дней без банковской карты. Если оплата не поступила,
            действует льготный период 3 дня. Данные Заказчика хранятся 90 дней.
          </p>
        </OfferSection>

        <OfferSection title="6. Порядок оказания услуги">
          <p>
            Услуга оказывается онлайн. Доступ к оплаченному тарифу открывается в личном кабинете сразу
            после оплаты. Физическая доставка не осуществляется.
          </p>
          <p>
            Исполнитель не передает материальные носители, бумажные документы и товары. Для публикации
            Заказчик самостоятельно подключает свои аккаунты социальных сетей.
          </p>
        </OfferSection>

        <OfferSection title="7. Оплата и чек">
          <p>
            Оплата принимается через платежный сервис ЮKassa банковской картой или другим способом,
            доступным на платежной форме.
          </p>
          <p>
            Исполнитель является плательщиком налога на профессиональный доход. Чек формируется через
            приложение «Мой налог» и направляется на электронную почту покупателя.
          </p>
        </OfferSection>

        <OfferSection title="8. Возврат">
          <p>
            Заказчик вправе запросить возврат денежных средств за неиспользованные полные месяцы
            оплаченного периода. Текущий неполный месяц возврату не подлежит.
          </p>
          <p>
            Запрос направляется на электронную почту Исполнителя
            {hasLegalValue(requisites.email) ? (
              <>
                {' '}
                <a href={`mailto:${requisites.email}`} className={linkClass}>
                  {requisites.email}
                </a>
              </>
            ) : (
              ', указанную в реквизитах,'
            )}{' '}
            в течение 14 дней с даты оплаты. Возврат производится в течение 10 рабочих дней тем же
            способом, которым была совершена оплата.
          </p>
        </OfferSection>

        <OfferSection title="9. Ответственность сторон">
          <p>
            Исполнитель оказывает услугу с функциями, которые доступны в сервисе на момент оплаты, и
            поддерживает работоспособность сервиса, кроме перерывов на обслуживание и сбоев вне контроля
            Исполнителя.
          </p>
          <p>
            Исполнитель не отвечает за блокировки и сбои социальных сетей, за содержание материалов
            Заказчика и за последствия неверных настроек публикаций.
          </p>
          <p>
            Совокупная ответственность Исполнителя за один оплаченный период ограничена суммой, фактически
            полученной от Заказчика за этот период.
          </p>
          <p>
            Заказчик использует сервис законно и соблюдает правила подключенных социальных сетей.
          </p>
        </OfferSection>

        <OfferSection title="10. Персональные данные">
          <p>
            Для регистрации и оплаты Исполнитель обрабатывает персональные данные Заказчика: адрес
            электронной почты, данные учетной записи и сведения, которые платежный сервис передает для
            проведения платежа.
          </p>
          <p>
            Порядок обработки описан в <DocLink href="/legal/privacy">политике конфиденциальности</DocLink>.
            Текст согласия размещен на странице{' '}
            <DocLink href="/legal/personal-data">согласия на обработку персональных данных</DocLink>.
          </p>
          <p>Регистрируясь и оплачивая тариф, Заказчик подтверждает, что ознакомился с этими документами.</p>
        </OfferSection>

        <OfferSection title="11. Срок действия оферты">
          <p>
            Оферта вступает в силу с даты публикации редакции ({PUBLIC_OFFER_EDITION}) и действует до
            отзыва. Исполнитель может заменить оферту, опубликовав новую редакцию на этой странице.
          </p>
          <p>
            Новая редакция применяется к оплатам, совершенным после публикации. К уже оплаченному периоду
            применяются условия редакции, действовавшей в момент оплаты.
          </p>
          <p>
            Заказчик может прекратить использование сервиса в любой момент. Оплаченный доступ сохраняется
            до конца оплаченного периода, если возврат не запрошен по разделу 8.
          </p>
        </OfferSection>

        <OfferSection title="12. Реквизиты исполнителя">
          <p>Исполнитель оказывает услугу как плательщик налога на профессиональный доход (самозанятый).</p>
          <LegalRequisitesView rows={rows} className="mt-3" />
          <p>
            Запросы по договору, включая возврат, направляются на электронную почту Исполнителя
            {hasLegalValue(requisites.email) ? (
              <>
                :{' '}
                <a href={`mailto:${requisites.email}`} className={linkClass}>
                  {requisites.email}
                </a>
                .
              </>
            ) : (
              ', указанную выше.'
            )}
          </p>
        </OfferSection>
      </article>
    </main>
  );
}

function tariffPrice(plan: Plan, freePrice: string): string {
  if (plan.monthlyPrice === 0) {
    return freePrice;
  }
  const monthly = formatRub(priceForPeriod(plan.monthlyPrice, false));
  const yearly = formatRub(priceForPeriod(plan.monthlyPrice, true));
  return `${monthly} ₽ в месяц или ${yearly} ₽ в год`;
}

function OfferSection({ title, children }: { title: string; children: ReactNode }) {
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
