/**
 * I passi dell'onboarding, nell'ordine in cui si attraversano.
 *
 * Vivono fuori dalla pagina perché sia i testi sia l'indicatore di avanzamento
 * devono nominarli: l'ordine dell'array è l'ordine del wizard.
 */
export const ONBOARDING_STEPS = ['profile', 'import'] as const;

export type OnboardingStepId = (typeof ONBOARDING_STEPS)[number];
