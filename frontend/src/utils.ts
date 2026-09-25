export function formatFileSize(sizeInBytes: number): string {
    if (sizeInBytes < 1024) {
        return `${sizeInBytes} B`
    }

    const units = ['KB', 'MB', 'GB', 'TB']
    let value = sizeInBytes / 1024
    let unitIndex = 0

    while (value >= 1024 && unitIndex < units.length - 1) {
        value /= 1024
        unitIndex += 1
    }

    return `${value.toFixed(value >= 10 ? 0 : 1)} ${units[unitIndex]}`
}

export function toHex(buffer: ArrayBuffer): string {
    return [...new Uint8Array(buffer)]
        .map((byte) => byte.toString(16).padStart(2, '0'))
        .join('')
}

export async function mapConcurrent(items: any, concurrencyLimit: number, asyncFn: (arg0: any) => any) {
    const results = [];
    const executing = new Set();

    for (const item of items) {
        // Create the promise task
        const promise = Promise.resolve().then(() => asyncFn(item));
        results.push(promise);
        executing.add(promise);

        // Remove the promise from executing pool when settled
        const clean = () => executing.delete(promise);
        promise.then(clean, clean);

        // If limit reached, wait for at least one promise to finish before spawning the next
        if (executing.size >= concurrencyLimit) {
            await Promise.race(executing);
        }
    }

    return Promise.all(results);
}