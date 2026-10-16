declare module 'json-bigint' {
  function JSONbig(options?: { storeAsString?: boolean }): {
    parse: (text: string) => any
    stringify: (value: any) => string
  }
  export = JSONbig
}
