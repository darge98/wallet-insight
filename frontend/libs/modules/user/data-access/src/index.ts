export * from './lib/user.facade';
export * from './lib/http/user-http.providers';
// La forma JSON del profilo serve anche all'onboarding, che la riceve nella sua risposta.
export { toUserProfile, type UserResponse } from './lib/http/user-contract';
