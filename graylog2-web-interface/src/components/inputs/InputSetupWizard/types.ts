/*
 * Copyright (C) 2020 Graylog, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Server Side Public License, version 1,
 * as published by MongoDB, Inc.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * Server Side Public License for more details.
 *
 * You should have received a copy of the Server Side Public License
 * along with this program. If not, see
 * <http://www.mongodb.com/licensing/server-side-public-license>.
 */

import type { Input } from 'components/messageloaders/Types';
import type { StreamConfiguration } from 'components/inputs/InputSetupWizard/hooks/useSetupInputMutations';

export const INPUT_WIZARD_FLOWS = {
  ILLUMINATE: 'ILLUMINATE',
  NON_ILLUMINATE: 'NON_ILLUMINATE',
} as const;

export type InputSetupWizardFlow = (typeof INPUT_WIZARD_FLOWS)[keyof typeof INPUT_WIZARD_FLOWS];

export const INPUT_WIZARD_STEPS = {
  INSTALL_ILLUMINATE: 'INSTALL_ILLUMINATE',
  SELECT_ILLUMINATE: 'SELECT_ILLUMINATE',
  SETUP_ROUTING: 'SETUP_ROUTING',
  INPUT_DIAGNOSIS: 'INPUT_DIAGNOSIS',
  START_INPUT: 'START_INPUT',
} as const;

export type InputSetupWizardStep = (typeof INPUT_WIZARD_STEPS)[keyof typeof INPUT_WIZARD_STEPS];

export type WizardData = {
  input?: Input;
  flow: InputSetupWizardFlow;
};

export type OpenStepsData = {
  SETUP_ROUTING?: {
    streamId?: string;
    newStream?: StreamConfiguration;
    shouldCreateNewPipeline?: boolean;
    streamType: 'NEW' | 'EXISTING' | 'DEFAULT';
    removeMatchesFromDefault?: boolean;
  };
};
