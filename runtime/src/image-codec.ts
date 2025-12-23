/**
 * Image codec - re-exports browser implementation as default.
 * Node.js users get the node version via conditional exports.
 */
export { decodeImageAsync, encodeImageAsync, registerImageCodec } from "./image-codec.browser.js";
