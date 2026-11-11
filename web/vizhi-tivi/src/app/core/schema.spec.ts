import Ajv2020 from 'ajv/dist/2020';
import schema from '../../../../../contracts/catalogue-v1.schema.json';
import fixture from '../../../../../contracts/fixtures/catalogue-v1.json';

describe('Published JSON Schema', () => {
  const validate = new Ajv2020({ strict: false }).compile(schema);
  it('validates the shared development fixture', () => expect(validate(fixture)).toBe(true));
  it('rejects unsupported contract versions', () =>
    expect(validate({ ...fixture, schemaVersion: 2 })).toBe(false));
  it('rejects missing required metadata', () =>
    expect(validate({ ...fixture, channels: [{ id: 'test' }] })).toBe(false));
  it('enforces the Tamil number range', () =>
    expect(validate({ ...fixture, channels: [{ ...fixture.channels[0], number: 251 }] })).toBe(
      false,
    ));
  it('enforces the other-language number range', () =>
    expect(validate({ ...fixture, channels: [{ ...fixture.channels[1], number: 1 }] })).toBe(
      false,
    ));
  it('allows explicitly pending number assignments', () =>
    expect(validate({ ...fixture, channels: [{ ...fixture.channels[0], number: null }] })).toBe(
      true,
    ));
});
