import './App.css'

const apiFeatures = [
  'Authentication and access control',
  'File and folder metadata',
  'Presigned single-part and multipart uploads',
  'Authorized downloads',
  'File sharing and shared-with-me views',
]

const roadmap = [
  'Spring Boot backend with MongoDB and MinIO integration',
  'React frontend for upload, browsing, download, and sharing',
  'Docker Compose environment for local development',
]

function App() {
  return (
    <main className="app-shell">
      <section className="hero">
        <span className="hero__eyebrow">Dropbox clone learning project</span>
        <h1>Local-first scaffolding for the core file platform.</h1>
        <p className="hero__copy">
          This starter focuses on the first iteration: upload, download, sharing,
          multipart uploads, and the storage metadata model. Automatic sync stays
          out of scope for now.
        </p>
      </section>

      <section className="panel-grid">
        <article className="panel">
          <h2>Current API scope</h2>
          <ul>
            {apiFeatures.map((feature) => (
              <li key={feature}>{feature}</li>
            ))}
          </ul>
        </article>

        <article className="panel">
          <h2>Stack</h2>
          <dl className="stack-list">
            <div>
              <dt>Backend</dt>
              <dd>Spring Boot</dd>
            </div>
            <div>
              <dt>Frontend</dt>
              <dd>React + Vite</dd>
            </div>
            <div>
              <dt>Metadata</dt>
              <dd>MongoDB</dd>
            </div>
            <div>
              <dt>Object storage</dt>
              <dd>MinIO</dd>
            </div>
          </dl>
        </article>

        <article className="panel panel--wide">
          <h2>Implementation roadmap</h2>
          <ol>
            {roadmap.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ol>
        </article>
      </section>
    </main>
  )
}

export default App
